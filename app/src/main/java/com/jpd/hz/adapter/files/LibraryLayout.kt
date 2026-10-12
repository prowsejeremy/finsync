package com.jpd.hz.adapter.files

private const val MUSIC_FOLDER = "Music"
private const val BOOKS_FOLDER = "Audiobooks"
private const val PLAYLISTS_FOLDER = "Playlists"
private const val COVER = "folder.jpg"
private const val ARTIST_PHOTO = "artist.jpg"
private const val PLAYLIST_COVER_EXTENSION = "jpg"
// From a playlist file in Playlists/ up to the adapter's folder, where Music/ and Audiobooks/ are.
private const val PLAYLIST_TO_ADAPTER_FOLDER = "../"

/**
 * Where files go in an adapter's folder (spec "The Library folder format", "The contract"). Server
 * platforms build their paths here, so every server lays files out alike; each name is
 * sanitised. A storage platform keeps its source's own paths.
 */
object LibraryLayout {

    /** `Music/<album artist>/<album>/<file>`. */
    fun musicPath(albumArtist: String, album: String, fileName: String): String =
        "${artistFolder(albumArtist)}/${sanitizeFilename(album)}/${sanitizeFilename(fileName)}"

    /** `Music/<album artist>/artist.jpg`, the folder above the album's (T2). */
    fun artistPhotoPath(albumArtist: String): String = "${artistFolder(albumArtist)}/$ARTIST_PHOTO"

    /** `Audiobooks/<author>/<title>/<file>`. */
    fun bookPath(author: String, title: String, fileName: String): String =
        "$BOOKS_FOLDER/${sanitizeFilename(author)}/${sanitizeFilename(title)}/" +
            sanitizeFilename(fileName)

    /** `folder.jpg` beside the file at [path]. */
    fun coverBeside(path: String): String {
        val folder = path.substringBeforeLast('/', "")
        return if (folder.isEmpty()) COVER else "$folder/$COVER"
    }

    /** `Playlists/<name>.m3u8`. */
    fun playlistFilePath(fileName: String): String =
        "$PLAYLISTS_FOLDER/$fileName.${PlaylistFiles.EXTENSION}"

    /** `Playlists/<name>.jpg`, the playlist's cover beside its file. */
    fun playlistCoverPath(fileName: String): String =
        "$PLAYLISTS_FOLDER/$fileName.$PLAYLIST_COVER_EXTENSION"

    /** A playlist file's line for the file at [path]: relative to the playlist's folder. */
    fun playlistEntryPath(path: String): String = PLAYLIST_TO_ADAPTER_FOLDER + path

    private fun artistFolder(albumArtist: String) = "$MUSIC_FOLDER/${sanitizeFilename(albumArtist)}"
}
