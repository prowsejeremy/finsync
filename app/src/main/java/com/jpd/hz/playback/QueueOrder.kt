package com.jpd.hz.playback

import androidx.media3.common.Player
import kotlin.random.Random

/**
 * Queue arithmetic for BassPlayer and the queue sheet. "Entries" are the source's tracks, in the
 * order of the list they were played from; a "play order", the queue, lists entry indices in the
 * order they play, each at most once; and "positions" index the play order.
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

    /** Every entry, in source order. */
    fun albumOrder(size: Int): List<Int> = (0 until size).toList()

    /** In source order, the current track's position is its entry index. */
    fun positionAfterShuffleOff(order: List<Int>, current: Int): Int = order[current]

    /**
     * Whether [order] is a play order over [size] entries: not empty, every index inside, and
     * none twice. BassPlayer's queue relies on it, and Media3 rejects a playlist that holds one
     * entry twice.
     */
    fun isValidOrder(order: List<Int>, size: Int): Boolean =
        order.isNotEmpty() && order.all { it in 0 until size } &&
            order.distinct().size == order.size

    /**
     * Moves positions [from] until [to] so the first lands at [newIndex] in the result, as
     * Media3's moveMediaItems does; [newIndex] is clamped to the end.
     */
    fun move(order: List<Int>, from: Int, to: Int, newIndex: Int): List<Int> {
        val moved = order.subList(from, to)
        val rest = order.subList(0, from) + order.subList(to, order.size)
        val at = newIndex.coerceIn(0, rest.size)
        return rest.subList(0, at) + moved + rest.subList(at, rest.size)
    }

    /** Where the row at [position] lands when the row at [from] is dragged to [to]. */
    fun positionAfterMove(position: Int, from: Int, to: Int): Int = when {
        position == from -> to
        position in (from + 1)..to -> position - 1
        position in to until from -> position + 1
        else -> position
    }

    /**
     * Removes positions [from] until [to]. The rest keep their entry indices, because the source
     * keeps every track.
     */
    fun removeRange(order: List<Int>, from: Int, to: Int): List<Int> =
        order.filterIndexed { position, _ -> position < from || position >= to }

    /** Where [current] lands after a removal; a removed current track gives way to the next. */
    fun currentAfterRemove(current: Int, from: Int, to: Int, newSize: Int): Int = when {
        newSize == 0 -> 0
        current < from -> current
        current >= to -> current - (to - from)
        else -> from.coerceAtMost(newSize - 1)
    }
}
