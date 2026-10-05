package com.jpd.finsync.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterNavigationTest {

    private val starts = listOf(0L, 60_000L, 120_000L)

    @Test
    fun `previous restarts the chapter after more than 3 s of it`() {
        assertEquals(60_000L, previousChapterTarget(starts, 65_000L))
        assertEquals(60_000L, previousChapterTarget(starts, 63_001L))
    }

    @Test
    fun `previous within 3 s goes to the previous chapter, and from the first to the start`() {
        assertEquals(0L, previousChapterTarget(starts, 62_000L))
        assertEquals(60_000L, previousChapterTarget(starts, 123_000L))
        assertEquals(0L, previousChapterTarget(starts, 2_000L))
        assertEquals(0L, previousChapterTarget(listOf(5_000L, 60_000L), 1_000L))
    }

    @Test
    fun `next goes to the next chapter's start, and does nothing on the last`() {
        assertEquals(120_000L, nextChapterTarget(starts, 65_000L))
        assertNull(nextChapterTarget(starts, 130_000L))
        assertEquals(5_000L, nextChapterTarget(listOf(5_000L, 60_000L), 1_000L))
    }

    @Test
    fun `skips stay within the book`() {
        assertEquals(0L, skipTarget(10_000L, -15_000L, 100_000L))
        assertEquals(100_000L, skipTarget(90_000L, 30_000L, 100_000L))
        assertEquals(80_000L, skipTarget(50_000L, 30_000L, 100_000L))
    }

    @Test
    fun `an unknown length doesn't stop a skip forward`() {
        assertEquals(120_000L, skipTarget(90_000L, 30_000L, 0L))
    }

    @Test
    fun `the seek bar covers the current chapter, and the last runs to the end`() {
        assertEquals(
            ChapterWindow(1, 60_000L, 120_000L),
            chapterWindowAt(starts, 65_000L, 200_000L)
        )
        assertEquals(
            ChapterWindow(2, 120_000L, 200_000L),
            chapterWindowAt(starts, 150_000L, 200_000L)
        )
    }
}
