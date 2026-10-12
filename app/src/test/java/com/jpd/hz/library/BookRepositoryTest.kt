package com.jpd.hz.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.library.db.BookProgress
import com.jpd.hz.library.db.FileKind
import com.jpd.hz.library.db.LibraryBook
import com.jpd.hz.library.db.LibraryBookChapter
import com.jpd.hz.library.db.LibraryContents
import com.jpd.hz.library.db.LibraryDatabase
import com.jpd.hz.library.db.LibraryFile
import com.jpd.hz.library.scan.EmbeddedCovers
import com.jpd.hz.library.scan.deriveLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private const val LIBRARY = "/lib"
private const val ART_CACHE = "/cache"
private const val ID_PREFIX = "id-"
private const val HURRY = "Audiobooks/John Mark Comer/Hurry/Hurry.m4b"
private const val HURRY_ID = ID_PREFIX + HURRY
private const val HURRY_COVER = "Audiobooks/John Mark Comer/Hurry/cover.jpg"
private const val HURRY_MS = 10_000L
private const val SAPIENS = "Audiobooks/Yuval Noah Harari/Sapiens.m4b"
private const val SAPIENS_ID = ID_PREFIX + SAPIENS

/** Runs the book queries and rules against an in-memory library database, progress included. */
@RunWith(RobolectricTestRunner::class)
class BookRepositoryTest {

    private lateinit var library: LibraryDatabase
    private lateinit var repository: BookRepository

    private val hurryChapters = listOf(
        LibraryBookChapter(HURRY_ID, 0, "One", 0L),
        LibraryBookChapter(HURRY_ID, 1, "Two", 4_000L),
        LibraryBookChapter(HURRY_ID, 2, "Three", 8_000L)
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        library = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = BookRepository(
            library.libraryDao(),
            LibraryFiles({ File(LIBRARY) }, File(ART_CACHE))
        )
    }

    @After
    fun tearDown() {
        library.close()
    }

    // A file's ID is its path with a prefix, so no test can pass by mistaking one for the other.
    private fun idOf(path: String) = ID_PREFIX + path

    private fun book(path: String, title: String, durationMs: Long? = HURRY_MS) = LibraryBook(
        bookId = idOf(path), title = title, author = "Author", durationMs = durationMs,
        codec = null, bitDepth = null, sampleRate = null, bitrate = null, size = 1,
        coverPath = null, embeddedCover = null
    )

    /** Each book's path, by its ID, for books named with [idOf]. */
    private fun pathsOf(books: List<LibraryBook>) =
        books.associate { it.bookId to it.bookId.removePrefix(ID_PREFIX) }

    /**
     * Writes what a scan of [books] with [chapters] would, with Hurry's cover beside it, finding
     * each book at its entry in [paths].
     */
    private suspend fun scan(
        books: List<LibraryBook>,
        chapters: List<LibraryBookChapter> = emptyList(),
        paths: Map<String, String> = pathsOf(books)
    ) {
        val images = mapOf(HURRY_COVER.lowercase() to HURRY_COVER)
        val derived = deriveLibrary(
            emptyList(), books, paths, emptyList(), images, EmbeddedCovers { _, _ -> null }
        )
        val files = books.map { book ->
            LibraryFile(
                fileId = book.bookId,
                path = paths.getValue(book.bookId),
                size = 1,
                modifiedSec = 0,
                changedSec = 0,
                inode = 0,
                kind = FileKind.BOOK
            )
        }
        library.scanDao().replaceLibrary(
            LibraryContents(
                files = files,
                tracks = emptyList(),
                albums = derived.albums,
                artists = derived.artists,
                albumArtists = derived.albumArtists,
                trackArtists = derived.trackArtists,
                genres = derived.genres,
                trackGenres = derived.trackGenres,
                playlists = derived.playlists,
                playlistItems = derived.playlistItems,
                books = derived.books,
                chapters = chapters
            )
        )
    }

    private suspend fun progress(
        bookId: String,
        positionMs: Long,
        lastPlayedAt: Long,
        finished: Boolean = false
    ) = repository.saveBookProgress(BookProgress(bookId, positionMs, finished, lastPlayedAt))

    @Test
    fun `books list those in progress most recent first, then the rest A to Z`() = runBlocking {
        scan(
            listOf(
                book("Audiobooks/Zebra.m4b", "Zebra"),
                book("Audiobooks/Middle.m4b", "Middle"),
                book("Audiobooks/apple.m4b", "apple"),
                book("Audiobooks/Recent.m4b", "Recent"),
                book("Audiobooks/Done.m4b", "Done")
            )
        )
        progress(idOf("Audiobooks/Middle.m4b"), positionMs = 1_000L, lastPlayedAt = 10L)
        progress(idOf("Audiobooks/Recent.m4b"), positionMs = 1_000L, lastPlayedAt = 20L)
        progress(idOf("Audiobooks/Done.m4b"), positionMs = 0L, lastPlayedAt = 30L, finished = true)

        val books = repository.books().first()

        assertEquals(
            listOf("Recent", "Middle", "apple", "Done", "Zebra"),
            books.map { it.name }
        )
        assertEquals(BookStatus.Finished, books[3].status)
        assertEquals(BookStatus.NotStarted(HURRY_MS), books[4].status)
    }

    @Test
    fun `a book's page has its chapters and progress`() = runBlocking {
        scan(listOf(book(HURRY, "Hurry")), hurryChapters)
        progress(HURRY_ID, positionMs = 5_000L, lastPlayedAt = 1L)

        val detail = repository.book(HURRY_ID).first()!!

        assertEquals("Hurry", detail.name)
        assertEquals("Author", detail.author)
        assertEquals(HURRY_MS, detail.durationMs)
        assertEquals("$LIBRARY/$HURRY_COVER", detail.coverPath)
        assertEquals(
            listOf(Chapter("One", 0L), Chapter("Two", 4_000L), Chapter("Three", 8_000L)),
            detail.chapters
        )
        assertEquals(
            BookStatus.InProgress(chapterNumber = 2, leftMs = 5_000L, fraction = 0.5f),
            detail.status
        )
    }

    @Test
    fun `an unknown book is null`() = runBlocking {
        scan(listOf(book(HURRY, "Hurry")))

        assertNull(repository.book(idOf("Audiobooks/Unknown.m4b")).first())
    }

    @Test
    fun `a book without chapters has one chapter named after it`() = runBlocking {
        scan(listOf(book(SAPIENS, "Sapiens")))

        val whole = listOf(Chapter("Sapiens", 0L))
        assertEquals(whole, repository.book(SAPIENS_ID).first()?.chapters)
        assertEquals(whole, repository.playableBooks(listOf(SAPIENS_ID)).single()?.chapters)
    }

    @Test
    fun `playable books keep the order asked, with null for an unknown book`() = runBlocking {
        scan(listOf(book(HURRY, "Hurry"), book(SAPIENS, "Sapiens")), hurryChapters)
        val unknown = idOf("Audiobooks/Unknown.m4b")

        val playable = repository.playableBooks(listOf(SAPIENS_ID, unknown, HURRY_ID))

        assertEquals(listOf(SAPIENS_ID, null, HURRY_ID), playable.map { it?.book?.bookId })
        val hurry = playable[2]!!
        assertEquals("$LIBRARY/$HURRY", hurry.localPath)
        assertEquals("$LIBRARY/$HURRY_COVER", hurry.coverPath)
        assertEquals(listOf("One", "Two", "Three"), hurry.chapters.map { it.name })
        assertEquals("$LIBRARY/$SAPIENS", playable[0]?.localPath)
    }

    @Test
    fun `progress survives a rescan`() = runBlocking {
        val books = listOf(book(HURRY, "Hurry"))
        scan(books, hurryChapters)
        progress(HURRY_ID, positionMs = 5_000L, lastPlayedAt = 7L)

        scan(books, hurryChapters)

        assertEquals(5_000L, repository.bookProgress(HURRY_ID)?.positionMs)
        assertEquals(7L, repository.books().first().single().lastPlayedAt)
    }

    @Test
    fun `progress survives a rescan that finds the book moved`() = runBlocking {
        val books = listOf(book(HURRY, "Hurry"))
        val moved = "Audiobooks/Hurry.m4b"
        scan(books, hurryChapters)
        progress(HURRY_ID, positionMs = 5_000L, lastPlayedAt = 7L)

        scan(books, hurryChapters, paths = mapOf(HURRY_ID to moved))

        assertEquals(5_000L, repository.bookProgress(HURRY_ID)?.positionMs)
        val playable = repository.playableBooks(listOf(HURRY_ID)).single()!!
        assertEquals("$LIBRARY/$moved", playable.localPath)
    }

    @Test
    fun `a rescan without a book deletes its progress`() = runBlocking {
        val hurry = book(HURRY, "Hurry")
        val sapiens = book(SAPIENS, "Sapiens")
        scan(listOf(hurry, sapiens))
        progress(HURRY_ID, positionMs = 5_000L, lastPlayedAt = 1L)
        progress(SAPIENS_ID, positionMs = 2_000L, lastPlayedAt = 2L)

        scan(listOf(sapiens))

        assertNull(repository.bookProgress(HURRY_ID))
        assertEquals(2_000L, repository.bookProgress(SAPIENS_ID)?.positionMs)
        // Deleted, not hidden: the book coming back doesn't bring its progress back.
        scan(listOf(hurry, sapiens))
        assertNull(repository.bookProgress(HURRY_ID))
    }

    @Test
    fun `the book count counts every book`() = runBlocking {
        assertEquals(0, repository.bookCount().first())

        scan(listOf(book(HURRY, "Hurry"), book(SAPIENS, "Sapiens", durationMs = null)))

        assertEquals(2, repository.bookCount().first())
    }
}
