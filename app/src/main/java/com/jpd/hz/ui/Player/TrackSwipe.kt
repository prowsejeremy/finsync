package com.jpd.hz.ui

import android.animation.ValueAnimator
import android.view.View
import kotlin.math.abs

/**
 * The swipe for the next or previous track, on the mini-player and the Player (spec "Swipe").
 * [trackInfo], the track's art and details, follows the finger and fades; on release
 * [swipeOutcome] decides. A committed swipe slides out, runs the move, then slides back in from
 * the other side; anything else springs back. The controls stay put. Set it as [swipeLayout]'s
 * listener.
 */
class TrackSwipe(
    private val swipeLayout: HorizontalSwipeLayout,
    private val trackInfo: View,
    private val playback: PlaybackViewModel
) : HorizontalSwipeLayout.Listener {

    private val flingPxPerSec =
        SWIPE_FLING_DP_PER_SEC * swipeLayout.resources.displayMetrics.density
    // Where the block sat when this swipe started. It only places the block on screen, so
    // catching it mid-spring or mid-slide doesn't jump; the finger's own drag decides.
    private var startOffsetPx = 0f
    // From a committed release until its move has run; no new swipe starts meanwhile.
    private var committing = false

    init {
        trackInfo.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            // The screen is going: stop any slide. A move not yet run is dropped; for the
            // mini-player the controller is already released by then, so it would do nothing.
            override fun onViewDetachedFromWindow(view: View) = reset()
        })
    }

    override fun onSwipeStart(): Boolean {
        if (committing) return false
        trackInfo.animate().cancel()
        startOffsetPx = trackInfo.translationX
        return true
    }

    override fun onSwipeDrag(dxPx: Float) = show(startOffsetPx + dxPx)

    override fun onSwipeRelease(dxPx: Float, velocityPxPerSec: Float) {
        // The finger's own drag decides; where a caught block started only moves it on screen.
        val outcome = swipeOutcome(
            dragPx = dxPx,
            widthPx = swipeLayout.width.toFloat(),
            velocityPxPerSec = velocityPxPerSec,
            flingPxPerSec = flingPxPerSec,
            canGoNext = playback.canGoNext()
        )
        when (outcome) {
            SwipeOutcome.NEXT -> commit(LEFT) { playback.nextTrackOrChapter() }
            // Before the controller connects there's nothing to go back to.
            SwipeOutcome.PREVIOUS -> if (playback.canGoPrevious()) {
                commit(RIGHT) { playback.previousTrackOrChapter() }
            } else {
                springBack()
            }
            SwipeOutcome.NONE -> springBack()
        }
    }

    override fun onSwipeCancel() = springBack()

    private fun commit(direction: Float, move: () -> Unit) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            // "Remove animations" is on: the move runs and the block is simply put back.
            move()
            reset()
            return
        }
        committing = true
        val distancePx = swipeLayout.width.toFloat()
        trackInfo.animate()
            .translationX(direction * distancePx)
            .alpha(0f)
            .setDuration(SWIPE_SLIDE_MS)
            .withEndAction {
                committing = false
                move()
                // In from the other side; the new track's details land as the controller updates.
                trackInfo.translationX = -direction * distancePx
                trackInfo.animate().translationX(0f).alpha(1f).setDuration(SWIPE_SLIDE_MS)
            }
    }

    private fun springBack() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            reset()
            return
        }
        trackInfo.animate().translationX(0f).alpha(1f).setDuration(SWIPE_SPRING_BACK_MS)
    }

    private fun show(offsetPx: Float) {
        trackInfo.translationX = offsetPx
        val fadeWidthPx = swipeLayout.width * SWIPE_FADE_FRACTION
        trackInfo.alpha =
            if (fadeWidthPx > 0f) (1f - abs(offsetPx) / fadeWidthPx).coerceIn(0f, 1f) else 1f
    }

    private fun reset() {
        trackInfo.animate().cancel()
        trackInfo.translationX = 0f
        trackInfo.alpha = 1f
        committing = false
    }

    private companion object {
        const val LEFT = -1f
        const val RIGHT = 1f
    }
}
