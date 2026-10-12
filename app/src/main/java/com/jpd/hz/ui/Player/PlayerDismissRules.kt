package com.jpd.hz.ui

// Swiping the Player down to close it (spec "Swipe the Player down to close it"), named so it can
// be adjusted on the phone.

/** The Player closes once it has moved this share of its height down. */
const val DISMISS_COMMIT_FRACTION = 0.25f

/** A downward flick at least this fast closes the Player however short the drag. */
const val DISMISS_FLING_DP_PER_SEC = 1_000f

/** A drag that doesn't close the Player springs back to the top in this time. */
const val DISMISS_SPRING_BACK_MS = 200L

/**
 * Decides a released drag (spec "When it closes"). It closes when the Player is [offsetPx] down
 * by at least a quarter of [heightPx], or on a downward flick of at least [flingPxPerSec]. A
 * flick upward doesn't close it, but doesn't stop a drag that's far enough. Distances and speeds
 * are in pixels; positive is down.
 */
fun shouldDismiss(
    offsetPx: Float,
    heightPx: Float,
    velocityPxPerSec: Float,
    flingPxPerSec: Float
): Boolean {
    if (offsetPx <= 0f) return false
    val farEnough = heightPx > 0f && offsetPx >= heightPx * DISMISS_COMMIT_FRACTION
    val flicked = velocityPxPerSec >= flingPxPerSec
    return farEnough || flicked
}
