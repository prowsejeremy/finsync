package com.jpd.hz.playback

import androidx.media3.common.Player
import kotlin.math.abs

/** −15 s and +30 s (spec "Skip"). */
const val SKIP_BACK_MS = 15_000L
const val SKIP_FORWARD_MS = 30_000L

/** The six book speeds (spec "Speed"). Music always plays at 1.0×. */
val BOOK_SPEEDS: List<Float> = listOf(0.8f, 1.0f, 1.2f, 1.5f, 1.75f, 2.0f)
const val DEFAULT_BOOK_SPEED = 1.0f

private const val PERCENT = 100f

/**
 * While a book plays, the system's previous and next skip 15 s back and 30 s on rather than
 * change chapter, so a stray tap doesn't lose your place (spec "System controls"). Null for any
 * other seek, which goes through as asked.
 */
fun systemSkipMs(seekCommand: Int): Long? = when (seekCommand) {
    Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> -SKIP_BACK_MS
    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> SKIP_FORWARD_MS
    else -> null
}

/** BASS_FX's tempo change in percent: 1.2× is +20, 0.8× is −20. Pitch stays the same. */
fun tempoPercentFor(speed: Float): Float = (speed - 1f) * PERCENT

/** The step nearest [speed]. A stored value that isn't a positive number plays at 1.0×. */
fun nearestBookSpeed(speed: Float): Float {
    if (!speed.isFinite() || speed <= 0f) return DEFAULT_BOOK_SPEED
    return BOOK_SPEEDS.minBy { abs(it - speed) }
}
