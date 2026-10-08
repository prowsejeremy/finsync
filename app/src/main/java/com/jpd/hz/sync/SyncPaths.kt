package com.jpd.hz.sync

import com.jpd.hz.adapter.PlaylistEntry
import com.jpd.hz.adapter.PlaylistFiles
import com.jpd.hz.adapter.sanitizeFilename
import com.jpd.hz.library.bookAuthorOf
import com.jpd.hz.model.MediaItem
import java.io.File

private const val MUSIC_FOLDER = "Music"
private const val BOOKS_FOLDER = "Audiobooks"
private const val UNKNOWN_AUTHOR = "Unknown Author"
private const val BOOK_TYPE = "AudioBook"
private const val BOOK_COVER = "folder.jpg"
private const val ARTIST_PHOTO = "artist.jpg"
private const val PLAYLISTS_FOLDER = "Playlists"
private const val PLAYLIST_COVER_EXTENSION = "jpg"
// From a playlist file in Playlists/ up to the sync folder, where Music/ and Audiobooks/ are.
private const val PLAYLIST_TO_SYNC_FOLDER = "../"
private const val TICKS_PER_SECOND = 10_000_000L
private const val LABEL_SEPARATOR = " - "

// SyncEngine's path rules, moved here unchanged (3b) so the files-to-keep rule can be tested
// without Android. SyncEngine still calls them by the same names.

internal fun buildRelativePath(item: MediaItem): String {
    // Use the filename from the server if available, since it may contain a track number prefix that we don't want to lose.
    // If not, construct a filename ourselves.
    val filename = item.path
        ?.substringAfterLast('/')
        ?.let { sanitizeFilename(it) }
        ?: run {
            val track = item.trackNumber?.let { "%02d ".format(it) } ?: ""
            val name  = sanitizeFilename(item.name)
            val ext   = resolveExtension(item)
            "$track$name.$ext"
        }
    return "${albumFolder(item)}/$filename"
}

internal fun resolveExtension(item: MediaItem): String {
    item.path?.let { serverPath ->
        val ext = serverPath.substringAfterLast('.', "").lowercase()
        if (ext.isNotBlank() && ext.length <= 5 && !ext.contains('/')) return ext
    }
    item.container?.let { c ->
        val ext = c.split(',').first().trim().lowercase()
        if (ext.isNotBlank()) return ext
    }
    return "mp3"
}

internal fun buildArtworkPath(item: MediaItem): String = "${albumFolder(item)}/folder.jpg"

/** `Music/<album artist>/<album>`, beside Audiobooks so all the music sits in one folder. */
private fun albumFolder(item: MediaItem): String {
    val album  = sanitizeFilename(item.album ?: "Unknown Album")
    return "${artistFolder(item)}/$album"
}

/** `Music/<album artist>`, the folder above the album's, where its artist.jpg goes (T2). */
private fun artistFolder(item: MediaItem): String {
    val artist = sanitizeFilename(item.albumArtist ?: item.artists?.firstOrNull() ?: "Unknown Artist")
    return "$MUSIC_FOLDER/$artist"
}

/**
 * Each album-artist folder's photo, as its relative path to the artist whose photo it is (spec
 * "Other files it writes"). That's the first of the album's album artists, whose name the folder
 * carries. A folder named from a track artist gets none, and the first track in plan order wins.
 */
internal fun artistPhotosOf(tracks: List<MediaItem>): Map<String, String> {
    val photos = LinkedHashMap<String, String>()
    for (item in tracks) {
        if (item.albumArtist.isNullOrBlank()) continue
        val artistId = item.albumArtists.orEmpty().firstOrNull()?.id
        if (artistId.isNullOrBlank()) continue
        photos.putIfAbsent("${artistFolder(item)}/$ARTIST_PHOTO", artistId)
    }
    return photos
}

/** Each planned playlist's file name, without extension, by playlist ID. */
internal fun playlistFileNamesOf(plan: SyncPlan): Map<String, String> =
    PlaylistFiles.fileNamesOf(plan.playlists.map { it.playlistId to it.name })

/** `Playlists/<name>.m3u8` (spec "The Library folder format"). */
internal fun playlistFilePath(fileName: String): String =
    "$PLAYLISTS_FOLDER/$fileName.${PlaylistFiles.EXTENSION}"

/** `Playlists/<name>.jpg`, the playlist's cover beside its file. */
internal fun playlistCoverPath(fileName: String): String =
    "$PLAYLISTS_FOLDER/$fileName.$PLAYLIST_COVER_EXTENSION"

/** A playlist file's line for [item]: its path from Playlists/, length and "Artist - Title". */
internal fun playlistEntryOf(item: MediaItem): PlaylistEntry {
    val artist = item.artists?.firstOrNull { it.isNotBlank() } ?: item.albumArtist
    val label = if (artist.isNullOrBlank()) item.name else "$artist$LABEL_SEPARATOR${item.name}"
    return PlaylistEntry(
        path = PLAYLIST_TO_SYNC_FOLDER + syncRelativePath(item),
        seconds = item.runTimeTicks?.let { it / TICKS_PER_SECOND },
        label = label
    )
}

/** True for a Jellyfin AudioBook item. */
internal fun isBookItem(item: MediaItem): Boolean = item.type == BOOK_TYPE

/** Where an item goes in the sync folder: a book under Audiobooks, music under Music. */
internal fun syncRelativePath(item: MediaItem): String =
    if (isBookItem(item)) buildBookPath(item) else buildRelativePath(item)

/** `Audiobooks/<author>/<title>/<file>` (spec "Where files go"). */
internal fun buildBookPath(item: MediaItem): String {
    val filename = item.path
        ?.substringAfterLast('/')
        ?.let { sanitizeFilename(it) }
        ?: "${sanitizeFilename(item.name)}.${resolveExtension(item)}"
    return "${bookFolder(item)}/$filename"
}

/** A book's cover: folder.jpg beside its file. */
internal fun buildBookCoverPath(item: MediaItem): String = "${bookFolder(item)}/$BOOK_COVER"

/**
 * Books already on the device, by their files' paths: each file and the cover beside it. Sync
 * keeps these when the book list didn't load (spec "Order, cleanup and failures").
 */
internal fun bookFilesAt(localPaths: List<String>): Set<String> =
    localPaths.flatMapTo(HashSet()) { path ->
        val file = File(path)
        listOf(file.absolutePath, File(file.parentFile, BOOK_COVER).absolutePath)
    }

private fun bookFolder(item: MediaItem): String {
    val author = sanitizeFilename(bookAuthorOf(item) ?: UNKNOWN_AUTHOR)
    return "$BOOKS_FOLDER/$author/${sanitizeFilename(item.name)}"
}

/**
 * Every file sync keeps (spec "Order, cleanup and failures"): each planned track and its album's
 * folder art, and each planned book and its cover. From T2, also each album artist's photo and
 * each planned playlist's file and cover. The orphan cleanup deletes anything else, so
 * deselecting a book deletes its folder.
 */
internal fun filesToKeep(syncDir: File, plan: SyncPlan): Set<String> {
    val paths = mutableSetOf<String>()
    for (item in plan.tracks) {
        paths.add(File(syncDir, buildRelativePath(item)).absolutePath)
        paths.add(File(syncDir, buildArtworkPath(item)).absolutePath)
    }
    for (book in plan.books) {
        paths.add(File(syncDir, buildBookPath(book)).absolutePath)
        paths.add(File(syncDir, buildBookCoverPath(book)).absolutePath)
    }
    for (photoPath in artistPhotosOf(plan.tracks).keys) {
        paths.add(File(syncDir, photoPath).absolutePath)
    }
    for (fileName in playlistFileNamesOf(plan).values) {
        paths.add(File(syncDir, playlistFilePath(fileName)).absolutePath)
        paths.add(File(syncDir, playlistCoverPath(fileName)).absolutePath)
    }
    return paths
}
