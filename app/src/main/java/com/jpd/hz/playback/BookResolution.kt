package com.jpd.hz.playback

import com.jpd.hz.library.Chapter
import com.jpd.hz.library.PlayableBook

/** Everything needed to play and describe one book. */
data class ResolvedBook(
    val bookId: String,
    val path: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val durationMs: Long?,
    val codec: String?,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val fileSize: Long,
    /** Never empty (spec "No chapters from the server"). */
    val chapters: List<Chapter>
)

/**
 * Null when the book's file is gone ([fileLength] returns null), so playback shows "Files
 * missing. Rescan your library." The size comes from the file itself.
 */
fun resolveBook(source: PlayableBook, fileLength: (String) -> Long?): ResolvedBook? {
    val size = fileLength(source.localPath) ?: return null
    val book = source.book
    return ResolvedBook(
        bookId = book.bookId,
        path = source.localPath,
        title = book.title,
        author = book.author,
        coverPath = source.coverPath,
        durationMs = book.durationMs,
        codec = book.codec,
        bitDepth = book.bitDepth,
        sampleRate = book.sampleRate,
        bitrate = book.bitrate,
        fileSize = size,
        chapters = source.chapters
    )
}
