package com.jpd.finsync.sync

import android.content.Context
import android.util.Log
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.library.PlaylistCovers
import com.jpd.finsync.library.stalePhotoFileNames
import com.jpd.finsync.model.MediaItem
import com.jpd.finsync.model.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "CoverSync"

/**
 * The very end of a sync: fetches the selected playlists' missing covers into private storage
 * (deleting covers no selected playlist needs) and each planned book's missing folder.jpg.
 * Failures are logged, never fail the sync, and are retried next time (spec "Order, cleanup and
 * failures").
 */
object CoverSync {

    suspend fun run(
        context: Context,
        config: ServerConfig,
        jellyfin: JellyfinRepository,
        syncDir: File,
        plan: SyncPlan
    ) {
        try {
            syncPlaylistCovers(context, config, jellyfin, plan.playlistIds)
            syncBookCovers(config, jellyfin, syncDir, plan.books)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Covers skipped", e)
        }
    }

    // filesToKeep lists each book's cover, so orphan cleanup keeps it between syncs.
    private suspend fun syncBookCovers(
        config: ServerConfig,
        jellyfin: JellyfinRepository,
        syncDir: File,
        books: List<MediaItem>
    ) {
        for (book in books) {
            val cover = File(syncDir, buildBookCoverPath(book))
            if (!cover.isFile) fetchPrimaryImage(jellyfin, config, book.id, cover)
        }
    }

    private suspend fun syncPlaylistCovers(
        context: Context,
        config: ServerConfig,
        jellyfin: JellyfinRepository,
        playlistIds: Set<String>
    ) {
        val folder = PlaylistCovers.folder(context)
        withContext(Dispatchers.IO) { folder.mkdirs() }
        for (playlistId in playlistIds) {
            val cover = PlaylistCovers.file(context, playlistId)
            // Fetched only when missing; the next sync retries any that failed.
            if (!cover.isFile) fetchPrimaryImage(jellyfin, config, playlistId, cover)
        }
        deleteStaleCovers(folder, playlistIds)
    }

    // 3a's stale-photo rule fits: <id>.jpg files to keep, everything else goes.
    private suspend fun deleteStaleCovers(folder: File, keepPlaylistIds: Set<String>) =
        withContext(Dispatchers.IO) {
            val names = folder.list()?.toList() ?: return@withContext
            for (name in stalePhotoFileNames(names, keepPlaylistIds)) {
                if (!File(folder, name).delete()) Log.w(TAG, "Couldn't delete playlist cover $name")
            }
        }
}
