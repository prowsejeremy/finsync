package com.jpd.finsync.sync

import android.content.Context
import android.os.Environment
import android.util.Log
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.auth.Result
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.db.SyncedAlbum
import com.jpd.finsync.db.SyncedTrack
import com.jpd.finsync.model.MediaItem
import com.jpd.finsync.model.ServerConfig
import com.jpd.finsync.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val TAG = "SyncEngine"

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

        val itemsResult = repo.getAllAudioItems(config)
        if (itemsResult is Result.Error) {
            emit(SyncState(isRunning = false, errorMessage = itemsResult.message), onProgress)
            return
        }

        val items = (itemsResult as Result.Success).data
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val selectedAlbumIds = prefs.getStringSet("selected_albums", emptySet()) ?: emptySet()
        
        val itemsToSync = if (selectedAlbumIds.isEmpty() || selectedAlbumIds.contains("all")) {
            items
        } else {
            items.filter { it.albumId != null && selectedAlbumIds.contains(it.albumId) }
        }

        Log.i(TAG, "Fetched ${itemsToSync.size} items from server to sync")
        emit(_syncState.value.copy(totalItems = itemsToSync.size), onProgress)

        val syncDir = getSyncDirectory(context, config)
        Log.i(TAG, "Sync directory: ${syncDir.absolutePath}")
        syncDir.mkdirs()

        if (!syncDir.canWrite()) {
            emit(SyncState(isRunning = false, errorMessage = "Cannot write to ${syncDir.absolutePath}"), onProgress)
            return
        }

        val expectedPaths = mutableSetOf<String>()
        for (item in itemsToSync) {
            expectedPaths.add(File(syncDir, buildRelativePath(item)).absolutePath)
            expectedPaths.add(File(syncDir, buildArtworkPath(item)).absolutePath)
        }

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

        itemsToSync.forEachIndexed { index, item ->
            if (!currentCoroutineContext().isActive) {
                Log.i(TAG, "Sync cancelled at track $index")
                repo.cancelAudioDownload()
                return@forEachIndexed
            }

            val localFile = File(syncDir, buildRelativePath(item))
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
        removeOrphanedFiles(syncDir, expectedPaths, dao)

        emit(
            SyncState(
                totalItems       = itemsToSync.size,
                downloadedItems  = itemsToSync.size,
                isRunning        = false,
                bytesDownloaded  = totalBytes,
                syncComplete     = true
            ), onProgress
        )
    }

    fun getSyncDirectory(context: Context, config: ServerConfig): File {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val custom = prefs.getString("sync_directory", null)
        if (!custom.isNullOrBlank()) return File(custom)

        val publicMusic = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "Finsync/${sanitizeFilename(config.serverName)}"
        )
        publicMusic.mkdirs()
        if (publicMusic.exists() && publicMusic.canWrite()) return publicMusic

        val appMusic = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            "Finsync/${sanitizeFilename(config.serverName)}"
        )
        return appMusic
    }

    fun getSyncDirectoryPath(context: Context, config: ServerConfig): String =
        getSyncDirectory(context, config).absolutePath

    private suspend fun needsDownload(
        dao: com.jpd.finsync.db.SyncDao,
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
        dao: com.jpd.finsync.db.SyncDao
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

    private fun buildRelativePath(item: MediaItem): String {
        val artist = sanitizeFilename(item.albumArtist ?: item.artists?.firstOrNull() ?: "Unknown Artist")
        val album  = sanitizeFilename(item.album ?: "Unknown Album")
        // Use the filename from the server if available, since it may contain a track number prefix that we don't want to lose.
        // If not, construct a filename ourselves.
        val filename = item.path
            ?.substringAfterLast('/')
            ?.let { sanitizeFilename(it) }
            ?: run {
                val track = item.trackNumber?.let { "%02d ".format(it) } ?: ""
                val name  = sanitizeFilename(item.name)
                val ext   = resolveExtension(item)
                "$track$name.$ext"
            }
        return "$artist/$album/$filename"
    }

    private fun resolveExtension(item: MediaItem): String {
        item.path?.let { serverPath ->
            val ext = serverPath.substringAfterLast('.', "").lowercase()
            if (ext.isNotBlank() && ext.length <= 5 && !ext.contains('/')) return ext
        }
        item.container?.let { c ->
            val ext = c.split(',').first().trim().lowercase()
            if (ext.isNotBlank()) return ext
        }
        return "mp3"
    }

    private fun buildArtworkPath(item: MediaItem): String {
        val artist = sanitizeFilename(item.albumArtist ?: item.artists?.firstOrNull() ?: "Unknown Artist")
        val album  = sanitizeFilename(item.album ?: "Unknown Album")
        return "$artist/$album/folder.jpg"
    }

    private fun buildTrackLabel(item: MediaItem): String {
        val artist = item.albumArtist ?: item.artists?.firstOrNull() ?: ""
        return if (artist.isNotBlank()) "$artist - ${item.name}" else item.name
    }

    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()

    private fun emit(state: SyncState, callback: ((SyncState) -> Unit)?) {
        _syncState.value = state
        callback?.invoke(state)
    }
}
