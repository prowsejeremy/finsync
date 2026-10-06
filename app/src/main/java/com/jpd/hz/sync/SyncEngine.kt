package com.jpd.hz.sync

import android.content.Context
import android.os.Environment
import android.util.Log
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.auth.Result
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.db.SyncedAlbum
import com.jpd.hz.db.SyncedTrack
import com.jpd.hz.library.LibraryRepository
import com.jpd.hz.library.PlaylistBookRows
import com.jpd.hz.library.SyncSelections
import com.jpd.hz.library.playlistRowsFrom
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.ServerCatalogue
import com.jpd.hz.model.ServerConfig
import com.jpd.hz.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "SyncEngine"
// Music and Audiobooks both sit under Media/hz/<server name> (folders in SyncPaths).
private const val SYNC_ROOT = "Media/hz"

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

        itemsToSync.forEachIndexed { index, item ->
            if (!currentCoroutineContext().isActive) {
                Log.i(TAG, "Sync cancelled at track $index")
                repo.cancelAudioDownload()
                return@forEachIndexed
            }

            // Music goes under Music/<artist>/<album>/, books under Audiobooks/<author>/<title>/.
            val localFile = File(syncDir, syncRelativePath(item))
            var isSuccessfullyProcessed = false
            var attempts = 0

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

                    try {
                        val response = repo.downloadAudio(config, item.id)
                        response.use { resp ->
                            if (resp.isSuccessful) {
                                val body = resp.body
                                if (body != null) {
                                    val written = writeStreamToFile(body.byteStream(), localFile)
                                    if (written > 0) {
                                        dao.upsertTrack(
                                            SyncedTrack(
                                                itemId       = item.id,
                                                localPath    = localFile.absolutePath,
                                                serverPath   = item.path,
                                                albumId      = item.albumId,
                                                fileSize     = written,
                                                dateModified = item.dateModified
                                            )
                                        )
                                        
                                        if (!localFile.exists()) {
                                            dao.deleteByLocalPath(localFile.absolutePath)
                                            attempts++
                                        } else {
                                            totalBytes += written
                                            downloadedCount++
                                            isSuccessfullyProcessed = true
                                        }
                                    } else {
                                        localFile.delete()
                                        attempts++
                                    }
                                } else {
                                    attempts++
                                }
                            } else {
                                attempts++
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        repo.cancelAudioDownload()
                        localFile.delete()
                        throw e
                    } catch (e: Exception) {
                        localFile.delete()
                        attempts++
                    }
                } else {
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
        ArtistPhotoSync.run(context, config, repo)
        // Covers come last; a failed one is logged and retried, never counted (3b spec). They
        // wait for a written catalogue too, as their cleanup could drop a failed playlist's cover.
        if (written != null) CoverSync.run(context, config, repo, syncDir, plan)

        emit(
            SyncState(
                totalItems       = itemsToSync.size,
                downloadedItems  = itemsToSync.size,
                isRunning        = false,
                bytesDownloaded  = totalBytes,
                syncComplete     = true,
                failedItems      = failedFetchCount(catalogue, selection) + failedDownloads
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
            LibraryRepository(context).writeCatalogue(catalogue)
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

    fun getSyncDirectory(context: Context, config: ServerConfig): File {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val custom = prefs.getString("sync_directory", null)
        if (!custom.isNullOrBlank()) return File(custom)

        val serverFolder = "$SYNC_ROOT/${sanitizeFilename(config.serverName)}"
        val publicMedia = File(Environment.getExternalStorageDirectory(), serverFolder)
        publicMedia.mkdirs()
        if (publicMedia.exists() && publicMedia.canWrite()) return publicMedia

        // Media isn't one of Android's standard folders, so writing it needs all-files access.
        return File(context.getExternalFilesDir(null), serverFolder)
    }

    fun getSyncDirectoryPath(context: Context, config: ServerConfig): String =
        getSyncDirectory(context, config).absolutePath

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
        val expectedLower = expectedPaths.map { it.lowercase() }.toSet()
        
        withContext(Dispatchers.IO) {
            syncDir.walkTopDown().forEach { file ->
                if (!file.isFile) return@forEach
                val abs = file.absolutePath.lowercase()
                if (abs !in expectedLower) {
                    file.delete()
                }
            }
            syncDir.walkBottomUp().forEach { dir ->
                if (dir.isDirectory && dir.absolutePath != syncDir.absolutePath) {
                    if (dir.listFiles()?.isEmpty() == true) dir.delete()
                }
            }
        }

        val allDbPaths = dao.getAllLocalPaths()
        for (path in allDbPaths) {
            if (!File(path).exists()) {
                dao.deleteByLocalPath(path)
            }
        }
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
