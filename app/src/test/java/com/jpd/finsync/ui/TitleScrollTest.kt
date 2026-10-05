package com.jpd.finsync.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleScrollTest {

    @Test
    fun `a title that fits has no overflow`() {
        assertEquals(0, titleOverflowPx(textWidthPx = 300, viewWidthPx = 320))
        assertEquals(0, titleOverflowPx(textWidthPx = 320, viewWidthPx = 320))
    }

    @Test
    fun `a title one pixel too wide overflows by one pixel`() {
        assertEquals(1, titleOverflowPx(textWidthPx = 321, viewWidthPx = 320))
    }

    @Test
    fun `scrolling takes the overflow over the speed, rounded up`() {
        // 30dp/s on a 3x screen is 90 px/s: 180 px takes 2 s, and 181 px a little more.
        assertEquals(2_000L, scrollDurationMs(overflowPx = 180, speedPxPerSec = 90f))
        assertEquals(2_012L, scrollDurationMs(overflowPx = 181, speedPxPerSec = 90f))
        assertEquals(0L, scrollDurationMs(overflowPx = 0, speedPxPerSec = 90f))
    }
}
