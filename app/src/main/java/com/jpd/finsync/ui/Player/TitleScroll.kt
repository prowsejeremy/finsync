package com.jpd.finsync.ui

import kotlin.math.ceil

// The Player title's cycle (spec "Player title"), named so it can be adjusted on the phone.

/** How long a too-long title holds at its start before scrolling. */
const val TITLE_HOLD_START_MS = 3_000L

/** How fast it scrolls. */
const val TITLE_SCROLL_DP_PER_SEC = 30f

/** How long it holds once its end is fully visible. */
const val TITLE_HOLD_END_MS = 2_000L

/** Each fade on the way back to the start: out, then in. */
const val TITLE_FADE_MS = 300L

/** The fading edge on the side with hidden text. */
const val TITLE_FADE_EDGE_DP = 24f

private const val MS_PER_SECOND = 1_000f

/** How much wider the title is than its view; 0 when it fits. */
fun titleOverflowPx(textWidthPx: Int, viewWidthPx: Int): Int =
    (textWidthPx - viewWidthPx).coerceAtLeast(0)

/** How long scrolling [overflowPx] takes at [speedPxPerSec], rounded up to whole milliseconds. */
fun scrollDurationMs(overflowPx: Int, speedPxPerSec: Float): Long {
    if (overflowPx <= 0 || speedPxPerSec <= 0f) return 0L
    return ceil(overflowPx * MS_PER_SECOND / speedPxPerSec).toLong()
}
