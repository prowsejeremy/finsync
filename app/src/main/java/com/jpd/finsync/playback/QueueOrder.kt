package com.jpd.finsync.playback

import androidx.media3.common.Player
import kotlin.random.Random

/**
 * Queue arithmetic for BassPlayer. "Entries" are tracks in album order; a "play order" lists
 * entry indices in the order they play, and "positions" index the play order.
 */
object QueueOrder {

    /** The position that plays after [current] when a track ends by itself. */
    fun autoNextIndex(current: Int, size: Int, repeatMode: Int): Int? = when {
        size == 0 -> null
        repeatMode == Player.REPEAT_MODE_ONE -> current
        current + 1 < size -> current + 1
        repeatMode == Player.REPEAT_MODE_ALL -> 0
        else -> null
    }

    /** Repeat cycles off → all → one → off. */
    fun nextRepeatMode(repeatMode: Int): Int = when (repeatMode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }

    /** A random play order over [size] entries that starts with entry [first]. */
    fun shuffledOrder(size: Int, first: Int, random: Random): List<Int> {
        if (size == 0) return emptyList()
        val rest = (0 until size).filter { it != first }.shuffled(random)
        return listOf(first) + rest
    }

    fun albumOrder(size: Int): List<Int> = (0 until size).toList()

    /** In album order, the current track's position is its entry index. */
    fun positionAfterShuffleOff(order: List<Int>, current: Int): Int = order[current]

    /** Inserts [newEntries] into the play order at [position], clamped to the end. */
    fun insert(order: List<Int>, position: Int, newEntries: List<Int>): List<Int> {
        val at = position.coerceIn(0, order.size)
        return order.subList(0, at) + newEntries + order.subList(at, order.size)
    }

    /**
     * Removes positions [from] until [to] and renumbers the remaining entry indices, so they
     * still run from 0 after those entries are dropped from the entry list.
     */
    fun removeRange(order: List<Int>, from: Int, to: Int): List<Int> {
        val removed = order.subList(from, to).toSet()
        val kept = order.filterIndexed { position, _ -> position < from || position >= to }
        return kept.map { entry -> entry - removed.count { it < entry } }
    }

    /** Where [current] lands after a removal; a removed current track gives way to the next. */
    fun currentAfterRemove(current: Int, from: Int, to: Int, newSize: Int): Int = when {
        newSize == 0 -> 0
        current < from -> current
        current >= to -> current - (to - from)
        else -> from.coerceAtMost(newSize - 1)
    }
}
