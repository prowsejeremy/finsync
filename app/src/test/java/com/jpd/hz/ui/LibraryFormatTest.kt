package com.jpd.hz.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFormatTest {

    @Test
    fun `durations under an hour are minutes and seconds`() {
        assertEquals("3:59", formatDuration(239_000L))
        assertEquals("0:05", formatDuration(5_400L))
        assertEquals("10:39", formatDuration(639_999L))
    }

    @Test
    fun `durations from an hour include hours`() {
        assertEquals("1:00:00", formatDuration(3_600_000L))
        assertEquals("1:02:03", formatDuration(3_723_000L))
    }

    @Test
    fun `negative durations show as zero`() {
        assertEquals("0:00", formatDuration(-1L))
    }

    @Test
    fun `album length sums durations and ignores unknown ones`() {
        assertEquals(79L, albumLengthMinutes(listOf(4_000_000L, 740_000L, null)))
    }

    @Test
    fun `album length rounds to the nearest minute`() {
        assertEquals(2L, albumLengthMinutes(listOf(90_000L)))
        assertEquals(1L, albumLengthMinutes(listOf(89_999L)))
    }

    @Test
    fun `joinWithDots skips missing parts`() {
        assertEquals("2018 · 13 tracks", joinWithDots(listOf("2018", "13 tracks")))
        assertEquals("13 tracks", joinWithDots(listOf(null, "13 tracks", "")))
    }
}
