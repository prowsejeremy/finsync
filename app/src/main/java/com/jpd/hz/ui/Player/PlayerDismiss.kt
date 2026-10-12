package com.jpd.hz.ui

import android.animation.ValueAnimator
import android.view.View

/**
 * Swiping the Player down closes it, as the chevron does (spec "Swipe the Player down to close
 * it"). [content], the whole Player, follows the finger down; on release [shouldDismiss] decides.
 * A close runs [dismiss], and the Player's slide-down carries on from where the finger left it;
 * anything else springs back to the top. Set it as [dragLayout]'s listener.
 */
class PlayerDismiss(
    private val dragLayout: SwipeDownLayout,
    private val content: View,
    private val dismiss: () -> Unit
) : SwipeDownLayout.Listener {

    private val flingPxPerSec =
        DISMISS_FLING_DP_PER_SEC * dragLayout.resources.displayMetrics.density
    // Where the Player sat when this drag started, so catching it mid-spring doesn't jump.
    private var startOffsetPx = 0f
    // From a committed release on, the Player is closing; no new drag starts.
    private var dismissing = false

    init {
        content.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            // The screen is going: stop any spring-back.
            override fun onViewDetachedFromWindow(view: View) = reset()
        })
    }

    override fun onDragStart(): Boolean {
        if (dismissing) return false
        content.animate().cancel()
        startOffsetPx = content.translationY
        return true
    }

    override fun onDrag(dyPx: Float) {
        content.translationY = offsetAfter(dyPx)
    }

    override fun onDragRelease(dyPx: Float, velocityPxPerSec: Float) {
        // Where the Player is shown decides, so a drag caught mid-spring counts from there.
        val offsetPx = offsetAfter(dyPx)
        content.translationY = offsetPx
        val close = shouldDismiss(
            offsetPx = offsetPx,
            heightPx = dragLayout.height.toFloat(),
            velocityPxPerSec = velocityPxPerSec,
            flingPxPerSec = flingPxPerSec
        )
        if (close) {
            dismissing = true
            dismiss()
        } else {
            springBack()
        }
    }

    override fun onDragCancel() {
        if (!dismissing) springBack()
    }

    // Never above its place: dragging back up past the top stops there.
    private fun offsetAfter(dyPx: Float): Float = (startOffsetPx + dyPx).coerceAtLeast(0f)

    private fun springBack() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            // "Remove animations" is on: the Player is simply put back.
            reset()
            return
        }
        content.animate().translationY(0f).setDuration(DISMISS_SPRING_BACK_MS)
    }

    private fun reset() {
        content.animate().cancel()
        content.translationY = 0f
        dismissing = false
    }
}
