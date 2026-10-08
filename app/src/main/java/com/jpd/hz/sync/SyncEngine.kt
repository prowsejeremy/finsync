package com.jpd.hz.sync

import android.content.Context
import android.util.Log
import com.jpd.hz.adapter.AdapterFiles
import com.jpd.hz.adapter.AdapterFolder
import com.jpd.hz.adapter.AdapterFolderStore
import com.jpd.hz.adapter.AdapterFolders
import com.jpd.hz.adapter.FileTagger
import com.jpd.hz.adapter.TagLibTagger
import com.jpd.hz.adapter.TagResult
import com.jpd.hz.adapter.cleanUpAdapterFolder
import com.jpd.hz.adapter.sanitizeFilename
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.auth.Result
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.db.SyncedAlbum
import com.jpd.hz.db.SyncedTrack
import com.jpd.hz.library.LibraryFolderStore
import com.jpd.hz.library.PlaylistBookRows
import com.jpd.hz.library.SyncSelections
import com.jpd.hz.library.playlistRowsFrom
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.ServerCatalogue
import com.jpd.hz.model.ServerConfig
import com.jpd.hz.model.SyncState
import com.jpd.hz.tags.TagFingerprint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "SyncEngine"
// This adapter's key in adapter_folders, and the name D4's suffix rule adds.
private const val ADAPTER = "jellyfin"
private const val PLATFORM = "Jellyfin"
// Where the folder was saved before T2. Read until the first adapter folder is saved.
private const val LEGACY_SYNC_DIRECTORY = "sync_directory"

object SyncEngine {

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> get() = _syncState

    fun emitStopped() {
        _syncState.value = SyncState(
            totalItems      = _syncState.value.totalItems,
            downloadedItems = _syncState.value.downloadedItems,
            isRunning       = false,
            wasStopped      = true
        )
    }

    suspend fun syncLibrary(
        context: Context,
        config: ServerConfig,
        // The edge where TagLib comes in. Unit tests never reach it (see FileTagger).
        tagger: FileTagger = TagLibTagger,
        onProgress: ((SyncState) -> Unit)? = null
    ) {
        val repo = JellyfinRepository(context)
        val dao  = SyncDatabase.getInstance(context).syncDao()

        emit(SyncState(isRunning = true, currentTrack = "Fetching library..."), onProgress)

        val catalogueResult = repo.getServerCatalogue(config)
        if (catalogueResult is Result.Error) {
            emit(SyncState(isRunning = false, errorMessage = catalogueResult.message), onProgress)
            return
        }

        val catalogue = (catalogueResult as Result.Success).data
        val written = writeCatalogue(context, catalogue)
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val selectedAlbumIds = prefs.getStringSet("selected_albums", emptySet()) ?: emptySet()
        val selections = SyncSelections(context)
        val selection =
            SyncSelection(selectedAlbumIds, selections.playlistIds(), selections.bookIds())
        // A failed write plans from this fetch alone, so cleanup waits for the next sync (below).
        val plan =
            syncPlanOf(catalogue, written ?: playlistRowsFrom(catalogue.playlists), selection)
        
        val itemsToSync = plan.items

        Log.i(TAG, "Fetched ${itemsToSync.size} items from server to sync")
        emit(_syncState.value.copy(totalItems = itemsToSync.size), onProgress)

        val syncDir = getSyncDirectory(context, config)
        Log.i(TAG, "Sync directory: ${syncDir.absolutePath}")
        syncDir.mkdirs()

        if (!syncDir.canWrite()) {
            emit(SyncState(isRunning = false, errorMessage = "Cannot write to ${syncDir.absolutePath}"), onProgress)
            return
        }

        val expectedPaths = filesToKeep(syncDir, plan) + keptBookFiles(dao, catalogue, selection)

        // Upsert album metadata upfront so partial syncs still appear in the library
        val albumGroups = itemsToSync.groupBy { it.albumId }.filterKeys { it != null }
        for ((albumId, tracks) in albumGroups) {
            val first = tracks.first()
            dao.upsertAlbum(
                SyncedAlbum(
                    albumId     = albumId!!,
                    name        = first.album ?: "Unknown Album",
                    albumArtist = first.albumArtist ?: first.artists?.firstOrNull(),
                    childCount  = tracks.size
                )
            )
        }

        var downloadedCount = 0
        var totalBytes = 0L
        // Counted after the usual two attempts; the next sync tries them again (3b spec).
        var failedDownloads = 0
        // Files left with their own tags; the next sync tries again (T2 spec).
        var untaggedFiles = 0

        itemsToSync.forEachIndexed { index, item ->
            if (!currentCoroutineContext().isActive) {
                Log.i(TAG, "Sync cancelled at track $index")
                repo.cancelAudioDownload()
                return@forEachIndexed
            }

            // Music goes under Music/<artist>/<album>/, books under Audiobooks/<author>/<title>/.
            val localFile = File(syncDir, syncRelativePath(item))
            val fields = JellyfinTagMapping.fieldsOf(item)
            var isSuccessfullyProcessed = false
            var attempts = 0
            // After a failed tag, the retry keeps its fresh download untagged (spec "Tagging
            // fails"). The first one was deleted, as a failed write can leave a file half-written.
            var tag = true

            while (!isSuccessfullyProcessed && attempts < 2) {
                val needsDownload = needsDownload(dao, localFile, item)

                if (needsDownload) {
                    emit(
                        _syncState.value.copy(
                            currentTrack = buildTrackLabel(item),
                            downloadedItems = index
                        ), onProgress
                    )

                    Log.d(TAG, "Downloading: ${item.name} -> ${localFile.absolutePath}")
                    localFile.parentFile?.mkdirs()

                    val attempt =
                        downloadAndTag(repo, config, dao, item, localFile, fields, tagger, tag)
                    when (attempt) {
                        Attempt.Failed -> attempts++
                        Attempt.TagFailed -> {
                            Log.w(TAG, "Couldn't tag ${localFile.name}; downloading it untagged")
                            tag = false
                            attempts++
                        }
                        is Attempt.Done -> {
                            totalBytes += attempt.bytes
                            downloadedCount++
                            if (!attempt.tagged) untaggedFiles++
                            isSuccessfullyProcessed = true
                        }
                    }
                } else {
                    val record = dao.getTrack(item.id)
                    val fingerprint = TagFingerprint.of(fields)
                    if (record != null && record.tagFingerprint != fingerprint) {
                        if (!retag(dao, record, localFile, fields, fingerprint, tagger)) {
                            untaggedFiles++
                        }
                    }
                    isSuccessfullyProcessed = true
                }
            }
            if (!isSuccessfullyProcessed) failedDownloads++

            val albumId = item.albumId
            if (albumId != null && dao.getAlbum(albumId)?.artworkPath == null) {
                val artFile = File(syncDir, buildArtworkPath(item))
                if (artFile.exists()) {
                    dao.setAlbumArtwork(albumId, artFile.absolutePath)
                } else {
                    artFile.parentFile?.mkdirs()
                    try {
                        val artResp = repo.downloadAlbumArt(config, albumId)
                        if (artResp.isSuccessful) {
                            artResp.body()?.let { b ->
                                val written = writeStreamToFile(b.byteStream(), artFile)
                                if (written > 0) dao.setAlbumArtwork(albumId, artFile.absolutePath)
                            }
                        }
                    } catch (e: Exception) {}
                }
            }

            emit(
                _syncState.value.copy(
                    downloadedItems = index + 1,
                    bytesDownloaded = totalBytes
                ), onProgress
            )
        }

        if (!currentCoroutineContext().isActive) return

        dao.deleteAlbumsWithNoTracks()
        // Without the written rows, the keep set could miss a failed part's files.
        if (written != null) removeOrphanedFiles(syncDir, expectedPaths, dao)
        // After orphan cleanup, so photos follow the albums that stayed (spec "Artist photos").
        ArtistPhotoSync.run(config, repo, syncDir, plan)
        // Covers and playlist files wait for a written catalogue: without one, the plan holds
        // only this fetch's playlists. A failed cover is logged and retried, never counted.
        if (written != null) {
            CoverSync.run(config, repo, syncDir, plan)
            PlaylistFileSync.run(syncDir, plan)
        }

        emit(
            SyncState(
                totalItems       = itemsToSync.size,
                downloadedItems  = itemsToSync.size,
                isRunning        = false,
                bytesDownloaded  = totalBytes,
                syncComplete     = true,
                failedItems      = failedFetchCount(catalogue, selection) + failedDownloads,
                untaggedFiles    = untaggedFiles
            ), onProgress
        )
    }

    // Before any download, so a cancelled sync still leaves the catalogue current. A failure
    // mustn't stop the sync itself. Returns the playlist and book rows written, or null.
    private suspend fun writeCatalogue(
        context: Context,
        catalogue: ServerCatalogue
    ): PlaylistBookRows? {
        return try {
            JellyfinCatalogue(context).write(catalogue)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't write the catalogue", e)
            null
        }
    }

    // A book list that didn't load plans no books, so the selected books already on the device
    // keep their files and covers (spec "Order, cleanup and failures").
    private suspend fun keptBookFiles(
        dao: com.jpd.hz.db.SyncDao,
        catalogue: ServerCatalogue,
        selection: SyncSelection
    ): Set<String> {
        if (!catalogue.booksFailed) return emptySet()
        return bookFilesAt(selection.bookIds.mapNotNull { dao.getTrack(it)?.localPath })
    }

    /**
     * The signed-in server's adapter folder (spec "The Jellyfin adapter", "Its folder"). It's
     * fixed the first time it's asked for, and saved in adapter_folders.
     */
    fun getSyncDirectory(context: Context, config: ServerConfig): File {
        val store = AdapterFolderStore(context)
        store.pathFor(ADAPTER, config.serverId)?.let { return File(it) }

        // A new server's folder goes in the Library folder (spec "Its folder").
        val folders = LibraryFolderStore(context)
        val savedLibrary = folders.saved()
        val publicLibrary = folders.publicDefault()
        val library = savedLibrary?.let(::File) ?: publicLibrary ?: folders.appDefault()
        val custom = customFolder(context)
        val path = AdapterFolders.chooseFolder(
            saved = store.all(),
            adapter = ADAPTER,
            serverId = config.serverId,
            legacy = custom ?: defaultFolder(library, config).takeIf(::hasFiles),
            library = library.absolutePath,
            serverName = config.serverName,
            platform = PLATFORM,
            hasFiles = ::hasFiles
        )
        // A folder in app storage, used only without all-files access, isn't saved. Once access
        // is granted, the next call settles on public Media/hz, as it did before T2.
        if (savedLibrary != null || publicLibrary != null || path == custom) {
            store.save(AdapterFolder(ADAPTER, config.serverId, path))
        }
        return File(path)
    }

    fun getSyncDirectoryPath(context: Context, config: ServerConfig): String =
        getSyncDirectory(context, config).absolutePath

    /** The Sync Directory picker: the signed-in server's adapter_folders entry (T2). */
    fun setSyncDirectory(context: Context, config: ServerConfig, path: String) {
        AdapterFolderStore(context).save(AdapterFolder(ADAPTER, config.serverId, path))
    }

    // The Sync Directory choice saved before T2, if any.
    private fun customFolder(context: Context): String? =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString(LEGACY_SYNC_DIRECTORY, null)
            ?.takeIf { it.isNotBlank() }

    // Where an install that kept the default synced before T2. It's built exactly as before, so
    // its files are found again.
    private fun defaultFolder(library: File, config: ServerConfig): String =
        File(library, sanitizeFilename(config.serverName)).absolutePath

    // A folder that can't be listed counts as holding files, so it's never taken over.
    private fun hasFiles(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        val names = file.list() ?: return true
        return names.isNotEmpty()
    }

    private suspend fun needsDownload(
        dao: com.jpd.hz.db.SyncDao,
        localFile: File,
        item: MediaItem
    ): Boolean {
        if (!localFile.exists() || localFile.length() == 0L) return true
        val record = dao.getTrack(item.id) ?: run {
            // File exists on disk but DB record was lost (e.g. after logout) — reconstruct and skip download
            dao.upsertTrack(
                SyncedTrack(
                    itemId       = item.id,
                    localPath    = localFile.absolutePath,
                    serverPath   = item.path,
                    albumId      = item.albumId,
                    fileSize     = localFile.length(),
                    dateModified = item.dateModified
                )
            )
            return false
        }
        if (record.serverPath != null && item.path != null && record.serverPath != item.path) return true
        if (localFile.length() != record.fileSize) return true
        if (item.dateModified != null && item.dateModified != record.dateModified) return true
        return false
    }

    private suspend fun removeOrphanedFiles(
        syncDir: File,
        expectedPaths: Set<String>,
        dao: com.jpd.hz.db.SyncDao
    ) {
        // Shared adapter code (D12): only ever inside this adapter's folder.
        withContext(Dispatchers.IO) { cleanUpAdapterFolder(syncDir, expectedPaths) }

        val allDbPaths = dao.getAllLocalPaths()
        for (path in allDbPaths) {
            if (!File(path).exists()) {
                dao.deleteByLocalPath(path)
            }
        }
    }

    /** How one download attempt went. */
    private sealed interface Attempt {
        /** Nothing landed; the caller tries once more. */
        object Failed : Attempt

        /** TagLib's write failed, so the download was deleted. The retry doesn't tag. */
        object TagFailed : Attempt

        class Done(val bytes: Long, val tagged: Boolean) : Attempt
    }

    // To "<file>.part", tagged there unless [tag] is false, then renamed into place (spec "Each
    // track or book during a sync"), so the file is never seen half-written or half-tagged. The
    // record holds the size after tagging, so needsDownload doesn't fetch a tagged file again.
    private suspend fun downloadAndTag(
        repo: JellyfinRepository,
        config: ServerConfig,
        dao: com.jpd.hz.db.SyncDao,
        item: MediaItem,
        localFile: File,
        fields: Map<String, String>,
        tagger: FileTagger,
        tag: Boolean
    ): Attempt {
        val part = AdapterFiles.partOf(localFile)
        try {
            val written = repo.downloadAudio(config, item.id).use { response ->
                val body = response.body
                if (response.isSuccessful && body != null) {
                    writeStreamToFile(body.byteStream(), part)
                } else {
                    0L
                }
            }
            if (written <= 0L) {
                part.delete()
                return Attempt.Failed
            }
            // The file and its record land together. A stop between them would leave the new
            // size unrecorded, and the next sync would download the file again.
            return withContext(NonCancellable) {
                // With no fields, finishDownload only renames.
                val toWrite = if (tag) fields else emptyMap()
                val result = withContext(Dispatchers.IO) {
                    AdapterFiles.finishDownload(part, localFile, toWrite, tagger)
                }
                if (result == TagResult.FAILED) {
                    return@withContext if (tag) Attempt.TagFailed else Attempt.Failed
                }
                if (result == TagResult.UNREADABLE) {
                    Log.w(TAG, "${localFile.name} keeps its own tags")
                }
                val tagged = tag && result == TagResult.TAGGED
                dao.upsertTrack(
                    SyncedTrack(
                        itemId         = item.id,
                        localPath      = localFile.absolutePath,
                        serverPath     = item.path,
                        albumId        = item.albumId,
                        fileSize       = localFile.length(),
                        dateModified   = item.dateModified,
                        tagFingerprint = if (tagged) TagFingerprint.of(fields) else null
                    )
                )
                Attempt.Done(written, tagged)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            repo.cancelAudioDownload()
            part.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't download ${item.name}", e)
            part.delete()
            return Attempt.Failed
        }
    }

    // A file already on the device whose fields changed on the server, or that was never tagged
    // (spec "Later syncs"). False when it keeps its own tags; its fingerprint stays as it was, so
    // the next sync tries again.
    private suspend fun retag(
        dao: com.jpd.hz.db.SyncDao,
        record: SyncedTrack,
        localFile: File,
        fields: Map<String, String>,
        fingerprint: String,
        tagger: FileTagger
    ): Boolean = withContext(NonCancellable) {
        // As in downloadAndTag: the renamed file and its new size are recorded together.
        val result = try {
            withContext(Dispatchers.IO) { AdapterFiles.retag(localFile, fields, tagger) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't re-tag ${localFile.name}", e)
            TagResult.FAILED
        }
        if (result != TagResult.TAGGED) {
            Log.w(TAG, "${localFile.name} keeps its own tags ($result)")
            return@withContext false
        }
        Log.d(TAG, "Re-tagged: ${localFile.absolutePath}")
        // The record names the file just tagged, which may have moved with the Sync Directory.
        dao.upsertTrack(
            record.copy(
                localPath = localFile.absolutePath,
                fileSize = localFile.length(),
                tagFingerprint = fingerprint
            )
        )
        true
    }

    private suspend fun writeStreamToFile(
        stream: java.io.InputStream,
        dest: File
    ): Long = withContext(Dispatchers.IO) {
        var written = 0L
        try {
            FileOutputStream(dest).use { out ->
                stream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        written += read
                    }
                    out.flush()
                }
            }
        } catch (e: Exception) {
            dest.delete()
            written = 0L
        }
        written
    }

    private fun buildTrackLabel(item: MediaItem): String {
        val artist = item.albumArtist ?: item.artists?.firstOrNull() ?: ""
        return if (artist.isNotBlank()) "$artist - ${item.name}" else item.name
    }

    private fun emit(state: SyncState, callback: ((SyncState) -> Unit)?) {
        _syncState.value = state
        callback?.invoke(state)
    }
}
