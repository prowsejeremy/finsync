package com.jpd.hz.playback

import com.jpd.hz.library.Chapter
import com.jpd.hz.library.PlayableBook
import com.jpd.hz.library.db.LibraryBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookResolutionTest {

    private val source = PlayableBook(
        book = LibraryBook(
            bookId = "b1",
            title = "Dune",
            author = "Frank Herbert",
            durationMs = 3_600_000L,
            codec = "aac",
            bitDepth = null,
            sampleRate = 44_100,
            bitrate = 64_000,
            size = 999L,
            coverPath = "/books/folder.jpg",
            embeddedCover = null
        ),
        localPath = "/books/dune.m4b",
        coverPath = "/books/folder.jpg",
        chapters = listOf(Chapter("One", 0L), Chapter("Two", 1_800_000L))
    )

    @Test
    fun `a downloaded book resolves with its chapters, author and real file size`() {
        val resolved = checkNotNull(resolveBook(source) { 1_234L })
        assertEquals("b1", resolved.bookId)
        assertEquals("/books/dune.m4b", resolved.path)
        assertEquals("Frank Herbert", resolved.author)
        assertEquals(1_234L, resolved.fileSize)
        assertEquals(listOf(0L, 1_800_000L), resolved.chapters.map { it.startMs })
    }

    @Test
    fun `a book whose file is gone doesn't resolve`() {
        assertNull(resolveBook(source) { null })
    }
}
