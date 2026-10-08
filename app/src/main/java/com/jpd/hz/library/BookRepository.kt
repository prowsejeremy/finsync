package com.jpd.hz.library

import android.content.Context
import com.jpd.hz.library.db.BookProgress
import com.jpd.hz.library.db.LibraryBook
import com.jpd.hz.library.db.LibraryDao
import com.jpd.hz.library.db.LibraryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

// SQLite before 3.32 (Android before 11) allows 999 bound parameters in one statement.
private const val MAX_IDS_PER_QUERY = 900

/**
 * Where screens and the playback service read books from (overview rule 2): Audio Books, the
 * book page, the resolver's books and book progress, all in the library's database (A3).
 */
class BookRepository internal constructor(
    private val dao: LibraryDao,
    private val files: LibraryFiles
) {

    constructor(context: Context) : this(
        LibraryDatabase.getInstance(context.applicationContext).libraryDao(),
        LibraryFiles.of(context)
    )

    /** Every book: in progress first, most recent first, then A–Z (spec). */
    fun books(): Flow<List<BookSummary>> =
        combine(
            dao.observeBooks(),
            dao.observeAllChapters(),
            dao.observeBookProgress()
        ) { books, chapters, progress ->
            bookSummaries(books.map(::withCoverFile), chapters, progress)
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun bookCount(): Flow<Int> = dao.observeBookCount().distinctUntilChanged()

    /** The book page: the book, its chapters and its progress. Null once it's gone. */
    fun book(bookId: String): Flow<BookDetail?> =
        combine(
            dao.observeBook(bookId),
            dao.observeChapters(bookId),
            dao.observeProgress(bookId)
        ) { book, chapters, progress ->
            book?.let { bookDetailOf(withCoverFile(it), chapters, progress) }
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /**
     * What the resolver needs for each book among [bookIds], one per ID in the order asked, null
     * where the library has no such book. Chapters are never empty.
     */
    suspend fun playableBooks(bookIds: List<String>): List<PlayableBook?> =
        withContext(Dispatchers.IO) {
            val rows = bookIds.distinct()
                .chunked(MAX_IDS_PER_QUERY)
                .flatMap { chunk -> dao.books(chunk) }
                .associateBy { it.book.bookId }
            val chapters = rows.keys.toList()
                .chunked(MAX_IDS_PER_QUERY)
                .flatMap { chunk -> dao.chaptersOf(chunk) }
                .groupBy { it.bookId }
            bookIds.map { id ->
                rows[id]?.let { row ->
                    val book = withCoverFile(row.book)
                    PlayableBook(
                        book = book,
                        localPath = files.file(row.path).path,
                        coverPath = book.coverPath,
                        chapters = chaptersOrWhole(
                            chapters[id].orEmpty().map { Chapter(it.name, it.startMs) },
                            book.title
                        )
                    )
                }
            }
        }

    suspend fun bookProgress(bookId: String): BookProgress? = dao.bookProgress(bookId)

    /** Only the playback service's writer calls this (decision 16). */
    suspend fun saveBookProgress(progress: BookProgress) = dao.upsertBookProgress(progress)

    /** Logout, until T4 keeps progress on sign-out. */
    suspend fun clearBookProgress() = dao.deleteAllBookProgress()

    // The rules and screens take a book whose cover is a file they can load.
    private fun withCoverFile(book: LibraryBook): LibraryBook =
        book.copy(coverPath = files.art(book.coverPath, book.embeddedCover), embeddedCover = null)
}
