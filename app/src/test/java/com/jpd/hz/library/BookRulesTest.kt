package com.jpd.hz.library

import com.jpd.hz.library.db.BookProgress
import com.jpd.hz.library.db.LibraryBook
import com.jpd.hz.library.db.LibraryBookChapter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The player's book rules: chapters, progress, Resume, the Audio Books order and the book page.
 * The author and catalogue cases moved to Jellyfin's tests with their code (adapter harness spec,
 * "Where the code goes").
 */
class BookRulesTest {

    private val hourMs = 3_600_000L

    private fun book(id: String, name: String) = LibraryBook(
        bookId = id,
        title = name,
        author = "Author $id",
        durationMs = 10 * hourMs,
        codec = "aac",
        bitDepth = null,
        sampleRate = 44_100,
        bitrate = 64_000,
        size = 1L,
        coverPath = "/lib/Audiobooks/$id/folder.jpg",
        embeddedCover = null
    )

    private fun progress(
        id: String,
        positionMs: Long,
        finished: Boolean = false,
        lastPlayedAt: Long = 0L
    ) = BookProgress(id, positionMs, finished, lastPlayedAt)

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
    fun `progress reads not started, in progress with its chapter and time left, or finished`() {
        val starts = listOf(0L, hourMs, 2 * hourMs)
        val length = 10 * hourMs
        assertEquals(BookStatus.NotStarted(length), bookStatusOf(null, length, starts))
        assertEquals(BookStatus.NotStarted(length), bookStatusOf(progress("b", 0L), length, starts))
        val reading =
            bookStatusOf(progress("b", hourMs + 1L), length, starts) as BookStatus.InProgress
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
        val books = listOf(
            book("b1", "dune"), book("b2", "Emma"), book("b3", "Atlas"), book("b4", "Hobbit"),
            book("b5", "Carrie")
        )
        val saved = listOf(
            progress("b2", 1_000L, lastPlayedAt = 10L),
            progress("b4", 1_000L, lastPlayedAt = 20L),
            progress("b5", 10 * hourMs, finished = true, lastPlayedAt = 30L)
        )
        val listed = bookSummaries(books, emptyList(), saved)
        assertEquals(listOf("b4", "b2", "b3", "b5", "b1"), listed.map { it.bookId })
        assertEquals("/lib/Audiobooks/b4/folder.jpg", listed.first().coverPath)
    }

    @Test
    fun `a book's page has its chapters, or one named after it, and its progress`() {
        val chapters = listOf(
            LibraryBookChapter("b1", 0, "One", 0L),
            LibraryBookChapter("b1", 1, "Two", hourMs)
        )
        val detail = bookDetailOf(book("b1", "Dune"), chapters, progress("b1", hourMs + 1L))
        assertEquals(listOf(Chapter("One", 0L), Chapter("Two", hourMs)), detail.chapters)
        assertEquals(2, (detail.status as BookStatus.InProgress).chapterNumber)
        assertEquals("Dune", detail.name)
        assertEquals("/lib/Audiobooks/b1/folder.jpg", detail.coverPath)
        assertEquals(
            listOf(Chapter("Emma", 0L)),
            bookDetailOf(book("b2", "Emma"), emptyList(), null).chapters
        )
    }
}
