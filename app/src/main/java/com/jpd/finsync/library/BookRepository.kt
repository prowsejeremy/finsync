package com.jpd.finsync.library

import android.content.Context
import com.jpd.finsync.db.BookProgress
import com.jpd.finsync.db.CatalogueDao
import com.jpd.finsync.db.SyncDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

// SQLite before 3.32 (Android before 11) allows 999 bound parameters in one statement.
private const val MAX_IDS_PER_QUERY = 900

/**
 * Where screens and the playback service read books from (3b; overview rule 2): Audio Books, the
 * book page, Books to Sync, the book selection, the resolver's books and `book_progress`. The
 * catalogue write stays in [LibraryRepository], so every catalogue table swaps in one
 * transaction.
 */
class BookRepository internal constructor(
    context: Context,
    private val catalogueDao: CatalogueDao
) {

    constructor(context: Context) : this(
        context,
        SyncDatabase.getInstance(context.applicationContext).catalogueDao()
    )

    private val selections = SyncSelections(context.applicationContext)

    /** Selected, downloaded books: in progress first, most recent first, then A–Z (spec). */
    fun books(): Flow<List<BookSummary>> =
        combine(
            catalogueDao.observeDownloadedBooks(),
            catalogueDao.observeAllChapters(),
            catalogueDao.observeBookProgress()
        ) { books, chapters, progress ->
            // A book's cover is folder.jpg beside its file (spec "Where files go").
            bookSummaries(books, chapters, progress, selections.bookIds()) { row ->
                chooseArtwork(null, row.localPath, ::fileExists)
            }
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    // Home's count skips the progress and cover work.
    fun bookCount(): Flow<Int> =
        catalogueDao.observeDownloadedBooks()
            .conflate()
            .map { rows ->
                val selectedIds = selections.bookIds()
                rows.count { it.book.bookId in selectedIds }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** The book page: the book, its chapters and its progress. Null once it isn't downloaded. */
    fun book(bookId: String): Flow<BookDetail?> =
        combine(
            catalogueDao.observeDownloadedBook(bookId),
            catalogueDao.observeChapters(bookId),
            catalogueDao.observeProgress(bookId)
        ) { row, chapters, progress ->
            row?.let {
                bookDetailOf(it, chapters, progress, chooseArtwork(null, it.localPath, ::fileExists))
            }
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** Every audiobook on the server, A–Z, for Books to Sync. */
    fun bookChoices(): Flow<List<BookChoice>> =
        catalogueDao.observeBookChoices()
            .map { books -> books.map { BookChoice(it.bookId, it.name, it.author, it.size) } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** The books chosen in Books to Sync; empty means none (spec). */
    fun selectedIds(): Set<String> = selections.bookIds()

    fun setSelectedIds(ids: Set<String>) = selections.setBookIds(ids)

    /**
     * What the resolver needs for each downloaded book among [bookIds], one per ID in the order
     * asked, null where it isn't one. Chapters are never empty.
     */
    suspend fun playableBooks(bookIds: List<String>): List<PlayableBook?> =
        withContext(Dispatchers.IO) {
            val rows = bookIds.distinct()
                .chunked(MAX_IDS_PER_QUERY)
                .flatMap { chunk -> catalogueDao.downloadedBooks(chunk) }
                .associateBy { it.book.bookId }
            val chapters = rows.keys.toList()
                .chunked(MAX_IDS_PER_QUERY)
                .flatMap { chunk -> catalogueDao.chaptersOf(chunk) }
                .groupBy { it.bookId }
            bookIds.map { id ->
                rows[id]?.let { row ->
                    PlayableBook(
                        book = row.book,
                        localPath = row.localPath,
                        coverPath = chooseArtwork(null, row.localPath, ::fileExists),
                        chapters = chaptersOrWhole(
                            chapters[id].orEmpty().map { Chapter(it.name, it.startMs) },
                            row.book.name
                        )
                    )
                }
            }
        }

    suspend fun bookProgress(bookId: String): BookProgress? = catalogueDao.bookProgress(bookId)

    /** Only the playback service's writer calls this (decision 16). */
    suspend fun saveBookProgress(progress: BookProgress) =
        catalogueDao.upsertBookProgress(progress)

    /** Logout: progress belongs to this server's books (spec "Logout"). */
    suspend fun clearBookProgress() = catalogueDao.deleteAllBookProgress()

    private fun fileExists(path: String): Boolean = File(path).exists()
}
