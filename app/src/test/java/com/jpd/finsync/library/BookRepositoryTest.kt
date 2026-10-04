package com.jpd.finsync.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.finsync.db.BookProgress
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.db.SyncedTrack
import com.jpd.finsync.model.ChapterInfo
import com.jpd.finsync.model.MediaItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PREFS = "settings"

/** BookRepository, with books written by LibraryRepository, against an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class BookRepositoryTest {

    private lateinit var database: SyncDatabase
    private lateinit var library: LibraryRepository
    private lateinit var books: BookRepository

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = LibraryRepository(context, database.catalogueDao())
        books = BookRepository(context, database.catalogueDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    // An hour long, with a second chapter at 30 minutes.
    private fun book(id: String, name: String) = MediaItem(
        id = id,
        name = name,
        type = "AudioBook",
        runTimeTicks = 36_000_000_000L,
        chapters = listOf(ChapterInfo(0L, "Opening"), ChapterInfo(18_000_000_000L, "Middle"))
    )

    /** Marks [bookIds] as synced. A book's row has no album. */
    private suspend fun download(vararg bookIds: String) {
        for (bookId in bookIds) {
            database.syncDao().upsertTrack(
                SyncedTrack(
                    itemId = bookId,
                    localPath = "/music/Audiobooks/$bookId.m4b",
                    serverPath = null,
                    albumId = null,
                    fileSize = 1L
                )
            )
        }
    }

    @Test
    fun `book progress survives a catalogue refresh`() {
        runBlocking {
            val items = listOf(book("b1", "Dune"))
            library.writeCatalogue(emptyList(), books = items)
            books.saveBookProgress(BookProgress("b1", 60_000L, finished = false, lastPlayedAt = 5L))
            library.writeCatalogue(emptyList(), books = items)
            assertEquals(BookProgress("b1", 60_000L, false, 5L), books.bookProgress("b1"))
        }
    }

    @Test
    fun `clearing book progress, as logout does, empties it`() {
        runBlocking {
            books.saveBookProgress(BookProgress("b1", 60_000L, false, 5L))
            books.clearBookProgress()
            assertNull(books.bookProgress("b1"))
        }
    }

    @Test
    fun `Audio Books lists selected downloaded books with their progress`() {
        runBlocking {
            val items = listOf(book("b1", "Dune"), book("b2", "Emma"), book("b3", "Hobbit"))
            library.writeCatalogue(emptyList(), books = items)
            download("b1", "b2", "b3")
            books.setSelectedIds(setOf("b1", "b2"))
            books.saveBookProgress(BookProgress("b2", 1_860_000L, false, 9L))
            val listed = books.books().first()
            assertEquals(listOf("b2", "b1"), listed.map { it.bookId })
            val status = listed.first().status as BookStatus.InProgress
            assertEquals(2, status.chapterNumber)
            assertEquals(1_740_000L, status.leftMs)
            assertEquals(2, books.bookCount().first())
        }
    }
}
