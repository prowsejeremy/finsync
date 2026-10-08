package com.jpd.hz.sync

import android.util.Log
import com.jpd.hz.adapter.AdapterFiles
import com.jpd.hz.adapter.PlaylistFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val TAG = "PlaylistFileSync"

/**
 * Writes each selected playlist as Playlists/<name>.m3u8 in the sync folder (spec "Playlist
 * files"), for the player's scan and for other players. filesToKeep lists every one. Failures are
 * logged and never fail the sync; the next sync tries again.
 */
internal object PlaylistFileSync {

    suspend fun run(syncDir: File, plan: SyncPlan) {
        try {
            withContext(Dispatchers.IO) { writePlaylists(syncDir, plan) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Playlist files skipped", e)
        }
    }

    private fun writePlaylists(syncDir: File, plan: SyncPlan) {
        val fileNames = playlistFileNamesOf(plan)
        for (playlist in plan.playlists) {
            val fileName = fileNames[playlist.playlistId] ?: continue
            val text = PlaylistFiles.contentOf(playlist.name, playlist.items.map(::playlistEntryOf))
            try {
                AdapterFiles.writeIfChanged(File(syncDir, playlistFilePath(fileName)), text)
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't write playlist ${playlist.playlistId}", e)
            }
        }
    }
}
