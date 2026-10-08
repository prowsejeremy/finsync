package com.jpd.hz.sync

import android.util.Log
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "CoverSync"

/**
 * The end of a sync: fetches each selected playlist's missing cover, as Playlists/<name>.jpg, and
 * each planned book's missing folder.jpg, straight into the sync folder. filesToKeep lists them,
 * so cleanup removes covers nothing needs. Failures are logged, never fail the sync, and are
 * retried next time (spec "Order, cleanup and failures").
 */
internal object CoverSync {

    suspend fun run(
        config: ServerConfig,
        jellyfin: JellyfinRepository,
        syncDir: File,
        plan: SyncPlan
    ) {
        try {
            syncPlaylistCovers(config, jellyfin, syncDir, plan)
            syncBookCovers(config, jellyfin, syncDir, plan.books)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Covers skipped", e)
        }
    }

    private suspend fun syncPlaylistCovers(
        config: ServerConfig,
        jellyfin: JellyfinRepository,
        syncDir: File,
        plan: SyncPlan
    ) {
        val fileNames = playlistFileNamesOf(plan)
        for (playlist in plan.playlists) {
            val fileName = fileNames[playlist.playlistId] ?: continue
            val cover = File(syncDir, playlistCoverPath(fileName))
            if (!isFile(cover)) fetchPrimaryImage(jellyfin, config, playlist.playlistId, cover)
        }
    }

    private suspend fun syncBookCovers(
        config: ServerConfig,
        jellyfin: JellyfinRepository,
        syncDir: File,
        books: List<MediaItem>
    ) {
        for (book in books) {
            val cover = File(syncDir, buildBookCoverPath(book))
            if (!isFile(cover)) fetchPrimaryImage(jellyfin, config, book.id, cover)
        }
    }

    private suspend fun isFile(file: File): Boolean = withContext(Dispatchers.IO) { file.isFile }
}
