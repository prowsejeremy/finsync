package com.jpd.hz.library

import android.content.Context
import android.util.Log
import java.io.File

private const val TAG = "ArtistPhotos"
private const val PHOTO_FOLDER = "artist_images"
private const val PHOTO_SUFFIX = ".jpg"

/**
 * Artist photos live in private storage, not the music folder: folder names don't map to artist
 * IDs, and sync's orphan cleanup deletes files it doesn't know (spec "Artist photos"). A photo
 * exists when its file does; the database stores no path.
 */
object ArtistPhotos {

    fun folder(context: Context): File = File(context.filesDir, PHOTO_FOLDER)

    fun file(context: Context, artistId: String): File =
        File(folder(context), artistId + PHOTO_SUFFIX)

    fun pathIfExists(context: Context, artistId: String): String? =
        file(context, artistId).takeIf { it.isFile }?.path

    fun deleteAll(context: Context) {
        if (!folder(context).deleteRecursively()) Log.w(TAG, "Couldn't delete every artist photo")
    }
}

/**
 * The files in the photo folder that should go: photos of artists no downloaded album needs,
 * and anything else, such as a download that didn't finish.
 */
fun stalePhotoFileNames(fileNames: List<String>, keepArtistIds: Set<String>): List<String> =
    fileNames.filterNot { name ->
        name.endsWith(PHOTO_SUFFIX) && name.removeSuffix(PHOTO_SUFFIX) in keepArtistIds
    }
