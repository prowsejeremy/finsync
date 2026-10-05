package com.jpd.finsync.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeOutcomeTest {

    private val threshold = WIDTH * SWIPE_COMMIT_FRACTION

    private fun outcome(dragPx: Float, velocityPxPerSec: Float = 0f, canGoNext: Boolean = true) =
        swipeOutcome(dragPx, WIDTH, velocityPxPerSec, FLING, canGoNext)

    @Test
    fun `a drag short of a third does nothing, and a third commits`() {
        assertEquals(SwipeOutcome.NONE, outcome(-(threshold - 1f)))
        assertEquals(SwipeOutcome.NEXT, outcome(-threshold))
        assertEquals(SwipeOutcome.NONE, outcome(threshold - 1f))
        assertEquals(SwipeOutcome.PREVIOUS, outcome(threshold))
    }

    @Test
    fun `a flick commits a short drag`() {
        assertEquals(SwipeOutcome.NEXT, outcome(-20f, velocityPxPerSec = -FLING))
        assertEquals(SwipeOutcome.PREVIOUS, outcome(20f, velocityPxPerSec = FLING))
        assertEquals(SwipeOutcome.NONE, outcome(-20f, velocityPxPerSec = -(FLING - 1f)))
    }

    @Test
    fun `a flick against the drag doesn't commit it`() {
        assertEquals(SwipeOutcome.NONE, outcome(-20f, velocityPxPerSec = FLING * 2))
        assertEquals(SwipeOutcome.NONE, outcome(20f, velocityPxPerSec = -FLING * 2))
    }

    @Test
    fun `left is next only when there's a next`() {
        assertEquals(SwipeOutcome.NEXT, outcome(-WIDTH / 2, canGoNext = true))
        assertEquals(SwipeOutcome.NONE, outcome(-WIDTH / 2, canGoNext = false))
        assertEquals(SwipeOutcome.NONE, outcome(-20f, velocityPxPerSec = -FLING, canGoNext = false))
    }

    @Test
    fun `right is always previous`() {
        assertEquals(SwipeOutcome.PREVIOUS, outcome(WIDTH / 2, canGoNext = false))
        assertEquals(SwipeOutcome.PREVIOUS, outcome(WIDTH / 2, canGoNext = true))
    }

    private companion object {
        const val WIDTH = 900f
        const val FLING = 2_625f
    }
}
