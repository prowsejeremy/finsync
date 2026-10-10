package com.jpd.hz.platform.jellyfin

import com.jpd.hz.adapter.files.LibraryLayout
import com.jpd.hz.adapter.files.sanitizeFilename
import com.jpd.hz.platform.jellyfin.api.MediaItem

private const val BOOK_TYPE = "AudioBook"
private const val AUTHOR_KIND = "Author"
private const val NAME_SEPARATOR = ", "
private const val UNKNOWN_ARTIST = "Unknown Artist"
private const val UNKNOWN_ALBUM = "Unknown Album"
private const val UNKNOWN_AUTHOR = "Unknown Author"
private const val FALLBACK_EXTENSION = "mp3"
private const val MAX_EXTENSION_LENGTH = 5
private const val LABEL_SEPARATOR = " - "

/**
 * Where Jellyfin's items go in its folder, through the harness's shared layout (spec "The
 * contract"): music under `Music/<album artist>/<album>/`, books under
 * `Audiobooks/<author>/<title>/`, with the server's file name. Plain Kotlin; the golden tests
 * hold it to the paths sync wrote before the harness.
 */
object JellyfinLayout {

    fun isBook(item: MediaItem): Boolean = item.type == BOOK_TYPE

    /** Where [item] goes: a book under Audiobooks, music under Music. */
    fun pathOf(item: MediaItem): String =
        if (isBook(item)) {
            val author = bookAuthorOf(item) ?: UNKNOWN_AUTHOR
            LibraryLayout.bookPath(author, item.name, bookFileName(item))
        } else {
            val album = item.album ?: UNKNOWN_ALBUM
            LibraryLayout.musicPath(albumArtistOf(item), album, fileName(item))
        }

    /** "Artist - Title", for progress text and playlist lines: the track artist first. */
    fun labelOf(item: MediaItem): String {
        val artist = item.artists?.firstOrNull { it.isNotBlank() } ?: item.albumArtist
        return if (artist.isNullOrBlank()) item.name else "$artist$LABEL_SEPARATOR${item.name}"
    }

    /**
     * Each album-artist folder's photo, as its path to the artist whose photo it is (spec "Other
     * files it writes"): the first of the album's album artists, whose name the folder carries. A
     * folder named from a track artist gets none, and the first track in [tracks]' order wins.
     */
    fun artistPhotosOf(tracks: List<MediaItem>): Map<String, String> {
        val photos = LinkedHashMap<String, String>()
        for (item in tracks) {
            val albumArtist = item.albumArtist
            if (albumArtist.isNullOrBlank()) continue
            val artistId = item.albumArtists.orEmpty().firstOrNull()?.id
            if (artistId.isNullOrBlank()) continue
            photos.putIfAbsent(LibraryLayout.artistPhotoPath(albumArtist), artistId)
        }
        return photos
    }

    /**
     * A book's author: People entries of kind Author, else the album artist, else the artists.
     * Null when none is set, and the book goes under "Unknown Author".
     */
    fun bookAuthorOf(item: MediaItem): String? =
        bookAuthorsOf(item).takeIf { it.isNotEmpty() }?.joinToString(NAME_SEPARATOR)

    /** The same choice as a list, which sync writes to a book's tags (T2). */
    fun bookAuthorsOf(item: MediaItem): List<String> {
        val authors = item.people.orEmpty()
            .filter { it.type == AUTHOR_KIND }
            .mapNotNull { person -> person.name?.takeIf { it.isNotBlank() } }
        if (authors.isNotEmpty()) return authors
        item.albumArtist?.takeIf { it.isNotBlank() }?.let { return listOf(it) }
        return item.artists.orEmpty().filter { it.isNotBlank() }
    }

    private fun albumArtistOf(item: MediaItem): String =
        item.albumArtist ?: item.artists?.firstOrNull() ?: UNKNOWN_ARTIST

    // The server's file name when it has one, which keeps a track-number prefix; else one built
    // as "01 Title.ext".
    private fun fileName(item: MediaItem): String =
        serverFileName(item) ?: run {
            val track = item.trackNumber?.let { "%02d ".format(it) } ?: ""
            "$track${sanitizeFilename(item.name)}.${extensionOf(item)}"
        }

    private fun bookFileName(item: MediaItem): String =
        serverFileName(item) ?: "${sanitizeFilename(item.name)}.${extensionOf(item)}"

    private fun serverFileName(item: MediaItem): String? =
        item.path?.substringAfterLast('/')?.let(::sanitizeFilename)

    private fun extensionOf(item: MediaItem): String {
        item.path?.let { serverPath ->
            val ext = serverPath.substringAfterLast('.', "").lowercase()
            if (ext.isNotBlank() && ext.length <= MAX_EXTENSION_LENGTH && !ext.contains('/')) {
                return ext
            }
        }
        item.container?.let { container ->
            val ext = container.split(',').first().trim().lowercase()
            if (ext.isNotBlank()) return ext
        }
        return FALLBACK_EXTENSION
    }
}
