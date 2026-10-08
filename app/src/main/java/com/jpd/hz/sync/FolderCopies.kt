package com.jpd.hz.sync

import android.content.Context
import android.util.Log
import com.jpd.hz.adapter.AdapterFiles
import com.jpd.hz.adapter.PlaylistFiles
import com.jpd.hz.library.ArtistPhotos
import com.jpd.hz.library.PlaylistCovers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val TAG = "FolderCopies"

/**
 * T2's extra files in the sync folder, for other players and for T3's scanner (spec "Other files
 * it writes"): each album artist's photo as `Music/<folder>/artist.jpg`, and each selected
 * playlist as `Playlists/<name>.m3u8` with its cover beside it. Photos and covers are copied from
 * private storage, where the player reads them until T3. filesToKeep lists every one of them.
 * Failures are logged and never fail the sync; the next sync tries again.
 */
internal object FolderCopies {

    suspend fun run(context: Context, syncDir: File, plan: SyncPlan, withPlaylists: Boolean) {
        try {
            withContext(Dispatchers.IO) {
                copyArtistPhotos(context, syncDir, plan)
                if (withPlaylists) writePlaylists(context, syncDir, plan)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Folder copies skipped", e)
        }
    }

    // Only beside music that's on the device, and only once sync has fetched the photo.
    private fun copyArtistPhotos(context: Context, syncDir: File, plan: SyncPlan) {
        for ((path, artistId) in artistPhotosOf(plan.tracks)) {
            val photo = ArtistPhotos.file(context, artistId)
            val target = File(syncDir, path)
            if (photo.isFile && target.parentFile?.isDirectory == true) copy(photo, target)
        }
    }

    private fun writePlaylists(context: Context, syncDir: File, plan: SyncPlan) {
        val fileNames = playlistFileNamesOf(plan)
        for (playlist in plan.playlists) {
            val fileName = fileNames[playlist.playlistId] ?: continue
            val text = PlaylistFiles.contentOf(playlist.name, playlist.items.map(::playlistEntryOf))
            try {
                AdapterFiles.writeIfChanged(File(syncDir, playlistFilePath(fileName)), text)
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't write playlist ${playlist.playlistId}", e)
                continue
            }
            val cover = PlaylistCovers.file(context, playlist.playlistId)
            if (cover.isFile) copy(cover, File(syncDir, playlistCoverPath(fileName)))
        }
    }

    private fun copy(source: File, target: File) {
        try {
            AdapterFiles.copyIfChanged(source, target)
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't copy ${source.name} to ${target.path}", e)
        }
    }
}
