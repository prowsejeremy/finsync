package com.jpd.finsync.ui

import kotlin.math.abs

// The mini-player swipe's tuning (spec "Constants"), named so it can be adjusted on the phone.

/** A drag this share of the card's width commits. */
const val SWIPE_COMMIT_FRACTION = 1f / 3f

/** A flick at least this fast, in the drag's direction, commits however short the drag. */
const val SWIPE_FLING_DP_PER_SEC = 1_000f

/** Each half of a committed swipe: sliding out, then sliding back in. */
const val SWIPE_SLIDE_MS = 150L

/** An uncommitted swipe's return to its place. */
const val SWIPE_SPRING_BACK_MS = 200L

/** The art and title are fully faded once dragged this share of the card's width. */
const val SWIPE_FADE_FRACTION = 0.5f

/** What a released mini-player swipe does. */
enum class SwipeOutcome { NEXT, PREVIOUS, NONE }

/**
 * Decides a released swipe (spec "Swipe"). It commits when dragged a third of [widthPx], or on a
 * flick of at least [flingPxPerSec] in the drag's direction. Left is NEXT, but NONE when
 * [canGoNext] is false; right is always PREVIOUS, which restarts the track or chapter when
 * there's nothing before it. Distances and speeds are in pixels; negative is left.
 */
fun swipeOutcome(
    dragPx: Float,
    widthPx: Float,
    velocityPxPerSec: Float,
    flingPxPerSec: Float,
    canGoNext: Boolean
): SwipeOutcome {
    if (dragPx == 0f) return SwipeOutcome.NONE
    val farEnough = widthPx > 0f && abs(dragPx) >= widthPx * SWIPE_COMMIT_FRACTION
    // Same sign as the drag only: a flick back the other way doesn't count.
    val flicked = velocityPxPerSec * dragPx > 0f && abs(velocityPxPerSec) >= flingPxPerSec
    return when {
        !farEnough && !flicked -> SwipeOutcome.NONE
        dragPx > 0f -> SwipeOutcome.PREVIOUS
        canGoNext -> SwipeOutcome.NEXT
        else -> SwipeOutcome.NONE
    }
}
