package com.jpd.finsync.library

import android.content.Context
import android.util.Log
import java.io.File

private const val TAG = "PlaylistCovers"
private const val COVER_FOLDER = "playlist_images"
private const val COVER_SUFFIX = ".jpg"

/**
 * Playlist covers live in private storage, as 3a's artist photos do: a playlist has no folder in
 * the music directory, and sync's orphan cleanup deletes files it doesn't know. A cover exists
 * when its file does.
 */
object PlaylistCovers {

    fun folder(context: Context): File = File(context.filesDir, COVER_FOLDER)

    fun file(context: Context, playlistId: String): File =
        File(folder(context), playlistId + COVER_SUFFIX)

    fun pathIfExists(context: Context, playlistId: String): String? =
        file(context, playlistId).takeIf { it.isFile }?.path

    fun deleteAll(context: Context) {
        if (!folder(context).deleteRecursively()) Log.w(TAG, "Couldn't delete every playlist cover")
    }
}
