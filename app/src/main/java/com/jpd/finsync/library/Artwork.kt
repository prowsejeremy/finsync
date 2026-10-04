package com.jpd.finsync.library

import java.io.File

private val FOLDER_ART_NAMES = listOf("folder.jpg", "folder.png")

/**
 * The album's artwork file: the stored path if its file exists, else folder.jpg or folder.png
 * beside one of its downloaded tracks. The stored path is often missing because sync clears it
 * on every run (spec, "Noticed, not in scope").
 */
fun chooseArtwork(storedPath: String?, trackPath: String?, exists: (String) -> Boolean): String? {
    if (storedPath != null && exists(storedPath)) return storedPath
    val folder = trackPath?.let { File(it).parentFile } ?: return null
    return FOLDER_ART_NAMES.map { File(folder, it).path }.firstOrNull(exists)
}

/**
 * Chooses each album's artwork once, so long song lists and queues don't repeat the same file
 * checks. Tracks without an album are checked one by one.
 */
class AlbumArtworkCache(private val exists: (String) -> Boolean) {

    private val byAlbum = HashMap<String, String?>()

    fun artworkFor(albumId: String?, storedPath: String?, trackPath: String?): String? {
        if (albumId == null) return chooseArtwork(storedPath, trackPath, exists)
        // containsKey, not getOrPut: an album with no artwork caches null too.
        if (!byAlbum.containsKey(albumId)) {
            byAlbum[albumId] = chooseArtwork(storedPath, trackPath, exists)
        }
        return byAlbum[albumId]
    }
}
