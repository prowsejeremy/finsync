package com.jpd.hz.library

import com.jpd.hz.db.BookProgress
import com.jpd.hz.db.CatalogueBook
import com.jpd.hz.db.CatalogueBookChapter
import com.jpd.hz.db.DownloadedBookRow
import com.jpd.hz.model.ChapterInfo
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.MediaSource
import com.jpd.hz.model.MediaStream
import com.jpd.hz.model.PersonInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookRulesTest {

    private val hourMs = 3_600_000L

    private fun book(id: String, name: String) = CatalogueBook(
        bookId = id,
        name = name,
        author = "Author $id",
        durationMs = 10 * hourMs,
        codec = "aac",
        bitDepth = null,
        sampleRate = 44_100,
        bitrate = 64_000,
        size = 1L
    )

    private fun row(id: String, name: String) = DownloadedBookRow(book(id, name), "/books/$id.m4b")

    private fun progress(
        id: String,
        positionMs: Long,
        finished: Boolean = false,
        lastPlayedAt: Long = 0L
    ) = BookProgress(id, positionMs, finished, lastPlayedAt)

    private fun audioBook(
        albumArtist: String? = null,
        artists: List<String>? = null,
        people: List<PersonInfo>? = null
    ) = MediaItem(
        id = "b",
        name = "Book",
        type = "AudioBook",
        albumArtist = albumArtist,
        artists = artists,
        people = people
    )

    @Test
    fun `the current chapter is the last started, the first before any start, the last after`() {
        val starts = listOf(5_000L, 60_000L, 120_000L)
        assertEquals(0, currentChapterIndex(starts, 1_000L))
        assertEquals(0, currentChapterIndex(starts, 5_000L))
        assertEquals(1, currentChapterIndex(starts, 119_999L))
        assertEquals(2, currentChapterIndex(starts, 120_000L))
        assertEquals(2, currentChapterIndex(starts, 9_999_999L))
    }

    @Test
    fun `a book without chapters is one chapter named after it`() {
        assertEquals(listOf(Chapter("Dune", 0L)), chaptersOrWhole(emptyList(), "Dune"))
        val chapters = listOf(Chapter("One", 0L))
        assertEquals(chapters, chaptersOrWhole(chapters, "Dune"))
    }

    @Test
    fun `chapter lengths run to the next start, and the last to the end of the book`() {
        assertEquals(
            listOf(60_000L, 60_000L, 80_000L),
            chapterLengthsMs(listOf(0L, 60_000L, 120_000L), 200_000L)
        )
    }

    @Test
    fun `the author comes from People, then the album artist, then the artists`() {
        val people = listOf(
            PersonInfo("Stephen Fry", "Narrator"),
            PersonInfo("J. K. Rowling", "Author")
        )
        assertEquals(
            "J. K. Rowling",
            bookAuthorOf(audioBook(albumArtist = "Someone", people = people))
        )
        assertEquals(
            "Someone",
            bookAuthorOf(audioBook(albumArtist = "Someone", artists = listOf("X")))
        )
        assertEquals("Ann, Bo", bookAuthorOf(audioBook(artists = listOf("Ann", "Bo"))))
        assertNull(bookAuthorOf(audioBook(people = listOf(PersonInfo("Nell", "Narrator")))))
    }

    @Test
    fun `book rows hold chapters in start order in milliseconds, with the source's details`() {
        val item = MediaItem(
            id = "b1",
            name = "Dune",
            type = "AudioBook",
            runTimeTicks = 36_000_000_000L,
            mediaSources = listOf(
                MediaSource(
                    id = "s1",
                    container = "m4b",
                    size = 412_000_000L,
                    mediaStreams = listOf(
                        MediaStream(
                            type = "Audio",
                            codec = "aac",
                            bitRate = 64_000,
                            sampleRate = 44_100
                        )
                    )
                )
            ),
            chapters = listOf(
                ChapterInfo(18_000_000_000L, "Two"),
                ChapterInfo(0L, "One"),
                ChapterInfo(27_000_000_000L, " ")
            )
        )
        val rows = bookRowsFrom(listOf(item))
        val expected = CatalogueBook(
            bookId = "b1",
            name = "Dune",
            author = null,
            durationMs = 3_600_000L,
            codec = "aac",
            bitDepth = null,
            sampleRate = 44_100,
            bitrate = 64_000,
            size = 412_000_000L
        )
        assertEquals(listOf(expected), rows.books)
        assertEquals(
            listOf(
                CatalogueBookChapter("b1", 0, "One", 0L),
                CatalogueBookChapter("b1", 1, "Two", 1_800_000L),
                CatalogueBookChapter("b1", 2, "Chapter 3", 2_700_000L)
            ),
            rows.chapters
        )
    }

    @Test
    fun `progress reads not started, in progress with its chapter and time left, or finished`() {
        val starts = listOf(0L, hourMs, 2 * hourMs)
        val length = 10 * hourMs
        assertEquals(BookStatus.NotStarted(length), bookStatusOf(null, length, starts))
        assertEquals(BookStatus.NotStarted(length), bookStatusOf(progress("b", 0L), length, starts))
        val reading = bookStatusOf(progress("b", hourMs + 1L), length, starts) as BookStatus.InProgress
        assertEquals(2, reading.chapterNumber)
        assertEquals(9 * hourMs - 1L, reading.leftMs)
        assertEquals(
            BookStatus.Finished,
            bookStatusOf(progress("b", length, finished = true), length, starts)
        )
    }

    @Test
    fun `Resume starts from the saved place, and a new or finished book starts over`() {
        assertEquals(0L, resumePositionMs(null))
        assertEquals(42_000L, resumePositionMs(progress("b", 42_000L)))
        assertEquals(0L, resumePositionMs(progress("b", 42_000L, finished = true)))
    }

    @Test
    fun `Audio Books puts books in progress first, latest first, then the rest A to Z`() {
        val rows = listOf(
            row("b1", "dune"), row("b2", "Emma"), row("b3", "Atlas"), row("b4", "Hobbit"),
            row("b5", "Carrie")
        )
        val saved = listOf(
            progress("b2", 1_000L, lastPlayedAt = 10L),
            progress("b4", 1_000L, lastPlayedAt = 20L),
            progress("b5", 10 * hourMs, finished = true, lastPlayedAt = 30L)
        )
        val selected = rows.map { it.book.bookId }.toSet()
        val listed = bookSummaries(rows, emptyList(), saved, selected) { null }
        assertEquals(listOf("b4", "b2", "b3", "b5", "b1"), listed.map { it.bookId })
    }

    @Test
    fun `Audio Books shows only selected books, and an empty selection shows none`() {
        val rows = listOf(row("b1", "Dune"), row("b2", "Emma"))
        assertEquals(
            listOf("b2"),
            bookSummaries(rows, emptyList(), emptyList(), setOf("b2")) { null }.map { it.bookId }
        )
        assertEquals(
            emptyList<String>(),
            bookSummaries(rows, emptyList(), emptyList(), emptySet()) { null }.map { it.bookId }
        )
    }
}
