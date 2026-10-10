package com.jpd.hz.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDismissRulesTest {

    private val threshold = HEIGHT * DISMISS_COMMIT_FRACTION

    private fun dismisses(
        offsetPx: Float,
        velocityPxPerSec: Float = 0f,
        heightPx: Float = HEIGHT
    ) = shouldDismiss(offsetPx, heightPx, velocityPxPerSec, FLING)

    @Test
    fun `a drag short of a quarter stays, and a quarter closes`() {
        assertFalse(dismisses(threshold - 1f))
        assertTrue(dismisses(threshold))
    }

    @Test
    fun `a downward flick closes a short drag`() {
        assertTrue(dismisses(20f, velocityPxPerSec = FLING))
        assertFalse(dismisses(20f, velocityPxPerSec = FLING - 1f))
    }

    @Test
    fun `an upward flick doesn't close a short drag, or stop a far one`() {
        assertFalse(dismisses(20f, velocityPxPerSec = -FLING * 2))
        assertTrue(dismisses(threshold, velocityPxPerSec = -FLING * 2))
    }

    @Test
    fun `no movement stays, even on a flick`() {
        assertFalse(dismisses(0f, velocityPxPerSec = FLING))
    }

    @Test
    fun `with no height only a flick closes`() {
        assertFalse(dismisses(threshold, heightPx = 0f))
        assertTrue(dismisses(20f, velocityPxPerSec = FLING, heightPx = 0f))
    }

    private companion object {
        const val HEIGHT = 2_000f
        const val FLING = 2_625f
    }
}
