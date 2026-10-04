package com.jpd.finsync.playback

/** Keys for the extras Finsync puts in each MediaItem's MediaMetadata. */
object TrackExtras {
    const val ALBUM_ID = "finsync.albumId"
    const val DURATION_MS = "finsync.durationMs"
    const val CODEC = "finsync.codec"
    const val BIT_DEPTH = "finsync.bitDepth"
    const val SAMPLE_RATE = "finsync.sampleRate"
    const val BITRATE = "finsync.bitrate"
    const val FILE_SIZE = "finsync.fileSize"
    // A book (3b) is one queue item; its chapters ride beside it as two arrays.
    const val IS_BOOK = "finsync.isBook"
    const val BOOK_AUTHOR = "finsync.bookAuthor"
    const val CHAPTER_STARTS = "finsync.chapterStarts"
    const val CHAPTER_NAMES = "finsync.chapterNames"
}
