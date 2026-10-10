package com.jpd.hz.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueOrderTest {

    @Test
    fun `repeat off moves to the next track`() {
        assertEquals(1, QueueOrder.autoNextIndex(0, 3, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun `repeat off ends after the last track`() {
        assertNull(QueueOrder.autoNextIndex(2, 3, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun `repeat all wraps to the first track`() {
        assertEquals(0, QueueOrder.autoNextIndex(2, 3, Player.REPEAT_MODE_ALL))
        assertEquals(2, QueueOrder.autoNextIndex(1, 3, Player.REPEAT_MODE_ALL))
    }

    @Test
    fun `repeat one plays the same track again`() {
        assertEquals(1, QueueOrder.autoNextIndex(1, 3, Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `an empty queue has no next track`() {
        assertNull(QueueOrder.autoNextIndex(0, 0, Player.REPEAT_MODE_ALL))
    }

    @Test
    fun `repeat cycles off, all, one`() {
        assertEquals(Player.REPEAT_MODE_ALL, QueueOrder.nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, QueueOrder.nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, QueueOrder.nextRepeatMode(Player.REPEAT_MODE_ONE))
    }

    @Test
    fun `shuffled order starts with the chosen track and keeps every track`() {
        val order = QueueOrder.shuffledOrder(10, 4, Random(1))
        assertEquals(4, order.first())
        assertEquals((0 until 10).toList(), order.sorted())
    }

    @Test
    fun `shuffled order is the same for the same seed`() {
        assertEquals(
            QueueOrder.shuffledOrder(10, 0, Random(42)),
            QueueOrder.shuffledOrder(10, 0, Random(42))
        )
    }

    @Test
    fun `shuffling one track or none`() {
        assertEquals(listOf(0), QueueOrder.shuffledOrder(1, 0, Random(1)))
        assertEquals(emptyList<Int>(), QueueOrder.shuffledOrder(0, 0, Random(1)))
    }

    @Test
    fun `turning shuffle off continues in album order from the current track`() {
        val order = listOf(3, 0, 4, 1, 2)
        val position = QueueOrder.positionAfterShuffleOff(order, 0)
        assertEquals(3, position)
        assertEquals(4, QueueOrder.autoNextIndex(position, 5, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun `move takes a track forward or back to newIndex`() {
        assertEquals(listOf(0, 2, 3, 1, 4), QueueOrder.move(listOf(0, 1, 2, 3, 4), 1, 2, 3))
        assertEquals(listOf(3, 0, 1, 2, 4), QueueOrder.move(listOf(0, 1, 2, 3, 4), 3, 4, 0))
    }

    @Test
    fun `move takes a range and keeps the entries of a shuffled queue`() {
        assertEquals(listOf(1, 4, 3, 0, 2), QueueOrder.move(listOf(3, 0, 1, 4, 2), 2, 4, 0))
        assertEquals(listOf(2, 3, 4, 0, 1), QueueOrder.move(listOf(0, 1, 2, 3, 4), 0, 2, 3))
    }

    @Test
    fun `move clamps newIndex past the end`() {
        assertEquals(listOf(1, 2, 0), QueueOrder.move(listOf(0, 1, 2), 0, 1, 9))
    }

    @Test
    fun `a dragged row lands where it's dropped`() {
        assertEquals(3, QueueOrder.positionAfterMove(1, 1, 3))
        assertEquals(1, QueueOrder.positionAfterMove(3, 3, 1))
    }

    @Test
    fun `rows between the drag's ends shift into the gap`() {
        assertEquals(1, QueueOrder.positionAfterMove(2, 1, 3))
        assertEquals(2, QueueOrder.positionAfterMove(3, 1, 3))
        assertEquals(2, QueueOrder.positionAfterMove(1, 3, 1))
        assertEquals(3, QueueOrder.positionAfterMove(2, 3, 1))
    }

    @Test
    fun `rows outside the drag stay`() {
        assertEquals(0, QueueOrder.positionAfterMove(0, 1, 3))
        assertEquals(4, QueueOrder.positionAfterMove(4, 1, 3))
    }

    @Test
    fun `a dragged row's effect matches the player's move`() {
        val size = 5
        for (from in 0 until size) {
            for (to in 0 until size) {
                val moved = QueueOrder.move((0 until size).toList(), from, from + 1, to)
                for (position in 0 until size) {
                    assertEquals(
                        moved.indexOf(position),
                        QueueOrder.positionAfterMove(position, from, to)
                    )
                }
            }
        }
    }

    @Test
    fun `a valid order is non-empty, inside the entries and has no entry twice`() {
        assertTrue(QueueOrder.isValidOrder(listOf(2, 0, 1), 3))
        assertFalse(QueueOrder.isValidOrder(emptyList(), 3))
        assertFalse(QueueOrder.isValidOrder(listOf(0, 3), 3))
        assertFalse(QueueOrder.isValidOrder(listOf(-1, 0), 3))
        assertFalse(QueueOrder.isValidOrder(listOf(0, 0), 3))
    }

    @Test
    fun `removeRange drops positions and keeps the others' entries`() {
        assertEquals(listOf(2, 1), QueueOrder.removeRange(listOf(2, 0, 3, 1), 1, 3))
    }

    @Test
    fun `current position after a removal`() {
        assertEquals(0, QueueOrder.currentAfterRemove(0, 1, 3, 2))
        assertEquals(1, QueueOrder.currentAfterRemove(3, 1, 3, 2))
        assertEquals(1, QueueOrder.currentAfterRemove(2, 1, 3, 2))
        assertEquals(0, QueueOrder.currentAfterRemove(1, 0, 4, 0))
    }
}
