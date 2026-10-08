package com.jpd.hz.library

import com.jpd.hz.library.db.BookProgress
import com.jpd.hz.library.db.LibraryBook
import com.jpd.hz.library.db.LibraryBookChapter

private val TITLE_ORDER: Comparator<BookSummary> =
    compareBy<BookSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.bookId }

/** Where a book stands, for Audio Books and the book page (spec "Audio Books"). */
sealed class BookStatus {
    data class NotStarted(val durationMs: Long) : BookStatus()
    data class InProgress(val chapterNumber: Int, val leftMs: Long, val fraction: Float) :
        BookStatus()
    object Finished : BookStatus()
}

fun bookStatusOf(
    progress: BookProgress?,
    durationMs: Long,
    chapterStartsMs: List<Long>
): BookStatus = when {
    progress?.finished == true -> BookStatus.Finished
    progress == null || progress.positionMs <= 0L -> BookStatus.NotStarted(durationMs)
    else -> BookStatus.InProgress(
        chapterNumber = currentChapterIndex(chapterStartsMs, progress.positionMs) + 1,
        leftMs = (durationMs - progress.positionMs).coerceAtLeast(0L),
        fraction = if (durationMs > 0L) {
            (progress.positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
    )
}

/** Where Resume or Play starts: the saved place, or 0 for a new or finished book (spec). */
fun resumePositionMs(progress: BookProgress?): Long =
    if (progress == null || progress.finished) 0L else progress.positionMs

/** Books in progress, most recently played first, then the rest A–Z (spec "Audio Books"). */
fun orderedBooks(books: List<BookSummary>): List<BookSummary> {
    val (inProgress, rest) = books.partition { it.status is BookStatus.InProgress }
    return inProgress.sortedByDescending { it.lastPlayedAt ?: 0L } + rest.sortedWith(TITLE_ORDER)
}

/** Audio Books: every book with its progress. */
fun bookSummaries(
    books: List<LibraryBook>,
    chapters: List<LibraryBookChapter>,
    progress: List<BookProgress>
): List<BookSummary> {
    val startsByBook = chapters.groupBy({ it.bookId }, { it.startMs })
    val progressByBook = progress.associateBy { it.bookId }
    val summaries = books.map { book ->
        val saved = progressByBook[book.bookId]
        val durationMs = book.durationMs ?: 0L
        BookSummary(
            bookId = book.bookId,
            name = book.title,
            author = book.author,
            durationMs = durationMs,
            coverPath = book.coverPath,
            status = bookStatusOf(saved, durationMs, startsByBook[book.bookId].orEmpty()),
            lastPlayedAt = saved?.lastPlayedAt
        )
    }
    return orderedBooks(summaries)
}

/** The book page's data, with the whole-book chapter when the file has none. */
fun bookDetailOf(
    book: LibraryBook,
    chapters: List<LibraryBookChapter>,
    progress: BookProgress?
): BookDetail {
    val durationMs = book.durationMs ?: 0L
    val bookChapters = chaptersOrWhole(chapters.map { Chapter(it.name, it.startMs) }, book.title)
    return BookDetail(
        bookId = book.bookId,
        name = book.title,
        author = book.author,
        durationMs = durationMs,
        coverPath = book.coverPath,
        chapters = bookChapters,
        status = bookStatusOf(progress, durationMs, bookChapters.map { it.startMs })
    )
}
