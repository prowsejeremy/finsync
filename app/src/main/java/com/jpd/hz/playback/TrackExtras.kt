package com.jpd.hz.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jpd.hz.library.Chapter
import com.jpd.hz.library.chaptersOrWhole

/** Keys for the extras hz puts in each MediaItem's MediaMetadata. */
object TrackExtras {
    const val ALBUM_ID = "hz.albumId"
    const val ALBUM_ARTIST_ID = "hz.albumArtistId"
    const val DURATION_MS = "hz.durationMs"
    const val CODEC = "hz.codec"
    const val BIT_DEPTH = "hz.bitDepth"
    const val SAMPLE_RATE = "hz.sampleRate"
    const val BITRATE = "hz.bitrate"
    const val FILE_SIZE = "hz.fileSize"
    // A book (3b) is one queue item; its chapters ride beside it as two arrays.
    const val IS_BOOK = "hz.isBook"
    const val BOOK_AUTHOR = "hz.bookAuthor"
    const val CHAPTER_STARTS = "hz.chapterStarts"
    const val CHAPTER_NAMES = "hz.chapterNames"
}

/** True for a book's queue item (3b). */
fun MediaItem.isBook(): Boolean =
    mediaMetadata.extras?.getBoolean(TrackExtras.IS_BOOK, false) == true

/** A book item's chapters from its extras; never empty (spec "No chapters from the server"). */
fun MediaItem.bookChapters(): List<Chapter> {
    val extras = mediaMetadata.extras
    val starts = extras?.getLongArray(TrackExtras.CHAPTER_STARTS) ?: LongArray(0)
    val names = extras?.getStringArray(TrackExtras.CHAPTER_NAMES) ?: emptyArray()
    val chapters = starts.indices.map { index ->
        Chapter(names.getOrElse(index) { "" }, starts[index])
    }
    return chaptersOrWhole(chapters, mediaMetadata.title?.toString() ?: "")
}

/**
 * What the notification, lock screen and Bluetooth show for a book: the chapter's name over the
 * book's title (spec "Labels"). Extras and artwork stay as they are.
 */
fun MediaItem.chapterMetadata(chapterIndex: Int): MediaMetadata {
    val bookTitle = mediaMetadata.title
    val chapterName = bookChapters().getOrNull(chapterIndex)?.name ?: bookTitle
    return mediaMetadata.buildUpon()
        .setTitle(chapterName)
        .setArtist(bookTitle)
        .build()
}
