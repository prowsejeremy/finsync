package com.jpd.finsync.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jpd.finsync.library.BookRepository
import com.jpd.finsync.library.LibraryRepository
import com.jpd.finsync.library.PlayableBook
import com.jpd.finsync.library.PlayableSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Turns queued Jellyfin IDs into playable file MediaItems at play time (overview rule 3). */
class TrackResolver(
    private val library: LibraryRepository,
    private val books: BookRepository
) {

    /**
     * One entry per ID, in order; null where neither a track nor a book can be played. Book IDs
     * resolve too, so ResumeStore can restore a book (spec "Queue").
     */
    suspend fun resolve(itemIds: List<String>): List<MediaItem?> = withContext(Dispatchers.IO) {
        val tracks = library.playableTracks(itemIds)
        val bookById = booksAmong(itemIds, tracks)
        itemIds.mapIndexed { index, id ->
            resolveTrack(tracks[index], ::fileLengthOrNull)?.toMediaItem()
                ?: bookById[id]?.let { resolveBook(it, ::fileLengthOrNull) }?.toMediaItem()
        }
    }

    /** The IDs that would resolve, for checking before anything is sent to the player. */
    suspend fun playableIds(itemIds: List<String>): Set<String> = withContext(Dispatchers.IO) {
        val tracks = library.playableTracks(itemIds)
        val bookById = booksAmong(itemIds, tracks)
        itemIds.filterIndexed { index, id ->
            resolveTrack(tracks[index], ::fileLengthOrNull) != null ||
                bookById[id]?.let { resolveBook(it, ::fileLengthOrNull) } != null
        }.toSet()
    }

    // Only IDs that aren't downloaded tracks are looked up as books, so music costs no extra query.
    private suspend fun booksAmong(
        itemIds: List<String>,
        tracks: List<PlayableSource?>
    ): Map<String, PlayableBook> {
        val others = itemIds.filterIndexed { index, _ -> tracks[index] == null }
        if (others.isEmpty()) return emptyMap()
        return books.playableBooks(others).filterNotNull().associateBy { it.book.bookId }
    }

    private fun fileLengthOrNull(path: String): Long? =
        File(path).takeIf { it.isFile }?.length()
}

private fun ResolvedTrack.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putLong(TrackExtras.FILE_SIZE, fileSize)
        albumId?.let { putString(TrackExtras.ALBUM_ID, it) }
        albumArtistId?.let { putString(TrackExtras.ALBUM_ARTIST_ID, it) }
        durationMs?.let { putLong(TrackExtras.DURATION_MS, it) }
        codec?.let { putString(TrackExtras.CODEC, it) }
        bitDepth?.let { putInt(TrackExtras.BIT_DEPTH, it) }
        sampleRate?.let { putInt(TrackExtras.SAMPLE_RATE, it) }
        bitrate?.let { putInt(TrackExtras.BITRATE, it) }
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artists)
        .setAlbumTitle(albumTitle)
        .setAlbumArtist(albumArtist)
        .setTrackNumber(trackNumber)
        .setDiscNumber(discNumber)
        // The session's default bitmap loader reads file:// URIs (verification result 3).
        .setArtworkUri(artworkPath?.let { Uri.fromFile(File(it)) })
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(itemId)
        .setUri(Uri.fromFile(File(path)))
        .setMediaMetadata(metadata)
        .build()
}

private fun ResolvedBook.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putBoolean(TrackExtras.IS_BOOK, true)
        putLong(TrackExtras.FILE_SIZE, fileSize)
        author?.let { putString(TrackExtras.BOOK_AUTHOR, it) }
        durationMs?.let { putLong(TrackExtras.DURATION_MS, it) }
        codec?.let { putString(TrackExtras.CODEC, it) }
        bitDepth?.let { putInt(TrackExtras.BIT_DEPTH, it) }
        sampleRate?.let { putInt(TrackExtras.SAMPLE_RATE, it) }
        bitrate?.let { putInt(TrackExtras.BITRATE, it) }
        putLongArray(TrackExtras.CHAPTER_STARTS, chapters.map { it.startMs }.toLongArray())
        putStringArray(TrackExtras.CHAPTER_NAMES, chapters.map { it.name }.toTypedArray())
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(author)
        .setAlbumTitle(title)
        .setArtworkUri(coverPath?.let { Uri.fromFile(File(it)) })
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(bookId)
        .setUri(Uri.fromFile(File(path)))
        .setMediaMetadata(metadata)
        .build()
}
