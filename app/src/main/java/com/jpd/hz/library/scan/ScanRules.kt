package com.jpd.hz.library.scan

// The types BASS plays with the bundled add-ons (spec "The Library folder format").
private val AUDIO_EXTENSIONS =
    setOf("mp3", "flac", "m4a", "m4b", "ogg", "opus", "ape", "wv", "wav", "aif", "aiff")
private val PLAYLIST_EXTENSIONS = setOf("m3u8", "m3u")
private val IMAGE_EXTENSIONS = setOf("jpg", "png")
private const val NO_MEDIA = ".nomedia"
private const val BOOKS_FOLDER = "Audiobooks"
// Album and book art beside the file, tried in this order.
private val FOLDER_ART = listOf("folder.jpg", "folder.png", "cover.jpg", "cover.png")
private val ARTIST_PHOTOS = listOf("artist.jpg", "artist.png")
private const val SEPARATOR = '/'

/**
 * The scanner's file rules, with no Android imports. Paths are relative to the Library folder
 * with "/" separators. [images] maps each image's lower-cased path to its path, so lookups ignore
 * case as shared storage does.
 */
object ScanRules {

    fun isAudio(name: String): Boolean = extensionOf(name) in AUDIO_EXTENSIONS

    fun isPlaylist(name: String): Boolean = extensionOf(name) in PLAYLIST_EXTENSIONS

    fun isImage(name: String): Boolean = extensionOf(name) in IMAGE_EXTENSIONS

    /** Hidden files and folders are skipped. */
    fun isHidden(name: String): Boolean = name.startsWith('.')

    /** A folder holding this file is skipped, with everything below it. */
    fun isNoMedia(name: String): Boolean = name.equals(NO_MEDIA, ignoreCase = true)

    /** A book: a folder named Audiobooks, in any case, anywhere in its path (D7). */
    fun isBook(path: String): Boolean =
        folderOf(path).split(SEPARATOR).any { it.equals(BOOKS_FOLDER, ignoreCase = true) }

    /** The lower-cased extension, without the dot; empty when there's none. */
    fun extensionOf(name: String): String =
        fileNameOf(name).substringAfterLast('.', "").lowercase()

    fun fileNameOf(path: String): String = path.substringAfterLast(SEPARATOR)

    /** The folder holding [path]; empty for the Library folder itself. */
    fun folderOf(path: String): String = path.substringBeforeLast(SEPARATOR, "")

    fun childOf(folder: String, name: String): String =
        if (folder.isEmpty()) name else "$folder$SEPARATOR$name"

    /** folder.jpg, folder.png, cover.jpg or cover.png beside [path], in that order. */
    fun folderArt(path: String, images: Map<String, String>): String? =
        firstImage(folderOf(path), FOLDER_ART, images)

    /**
     * The photo for the album whose first track is [path]: artist.jpg or artist.png in the folder
     * above the track's folder, unless that's the Library folder itself.
     */
    fun artistPhoto(path: String, images: Map<String, String>): String? {
        val albumFolder = folderOf(path)
        if (albumFolder.isEmpty()) return null
        val artistFolder = folderOf(albumFolder)
        if (artistFolder.isEmpty()) return null
        return firstImage(artistFolder, ARTIST_PHOTOS, images)
    }

    /** An image with the playlist file's name and a .jpg or .png extension, beside it. */
    fun playlistCover(path: String, images: Map<String, String>): String? {
        val base = path.substringBeforeLast('.')
        return IMAGE_EXTENSIONS.firstNotNullOfOrNull { images["$base.$it".lowercase()] }
    }

    private fun firstImage(folder: String, names: List<String>, images: Map<String, String>) =
        names.firstNotNullOfOrNull { images[childOf(folder, it).lowercase()] }
}
