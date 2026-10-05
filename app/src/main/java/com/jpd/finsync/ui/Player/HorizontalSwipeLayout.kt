package com.jpd.finsync.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

private const val VELOCITY_UNITS_PX_PER_SEC = 1_000

/**
 * Turns a sideways drag over its content into a swipe (spec "Swipe"). It takes a touch over only
 * once the finger has moved past the system touch slop, and more sideways than up or down; a
 * button under the finger then gets ACTION_CANCEL, so it doesn't click.
 *
 * Android gives a whole touch to the view that takes its ACTION_DOWN, so this layout must take
 * the touches its children don't, or it would never see a drag that starts on them. It's
 * clickable for that: a tap on its content is its own click (give it the card's listener), and
 * the buttons inside still take their own taps. Only the first finger counts: if it lifts while
 * another stays down, the swipe is cancelled (it springs back) and the rest is ignored.
 */
class HorizontalSwipeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** Hears one swipe from start to end. Distances and speeds are pixels; negative is left. */
    interface Listener {
        /**
         * A sideways drag passed the slop. Return false to have it ignored; it's still taken, so
         * it never becomes a tap.
         */
        fun onSwipeStart(): Boolean

        /** The finger is [dxPx] from where the swipe started. */
        fun onSwipeDrag(dxPx: Float)

        /** The finger lifted [dxPx] from the start, moving at [velocityPxPerSec]. */
        fun onSwipeRelease(dxPx: Float, velocityPxPerSec: Float)

        /** The touch was taken away, or this layout left the window, mid-swipe. */
        fun onSwipeCancel()
    }

    var listener: Listener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var velocityTracker: VelocityTracker? = null
    // The first finger down; other fingers are ignored, so they can't make the content jump.
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var swipeStartX = 0f
    private var swiping = false
    // Past the slop without becoming a swipe: the rest of this touch is left alone.
    private var settled = false
    // The listener accepted this swipe, so it hears the drag; a refused one is swallowed.
    private var reporting = false

    // Every event of a touch passes here once, whichever view ends up handling it.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startTouch(event)
            MotionEvent.ACTION_MOVE -> if (!swiping && !settled) checkForSwipe(event)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(event)
        }
        velocityTracker?.addMovement(event)
        val handled = super.dispatchTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) endTouch()
        return handled
    }

    // True once swiping: the child under the finger gets ACTION_CANCEL, and the rest comes here.
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = swiping

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Not a swipe: View's own tap handling, so a tap clicks and shows the ripple.
        if (!swiping) return super.onTouchEvent(event)
        // A refused or dropped swipe is swallowed: a sideways drag is never a tap.
        if (!reporting) return true
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            listener?.onSwipeCancel()
            return true
        }
        val x = firstFingerX(event) ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> listener?.onSwipeDrag(x - swipeStartX)
            MotionEvent.ACTION_UP -> release(x)
        }
        return true
    }

    // Taps reach this through super.onTouchEvent; overridden so lint sees clicks are handled.
    override fun performClick(): Boolean = super.performClick()

    override fun onDetachedFromWindow() {
        if (reporting) listener?.onSwipeCancel()
        endTouch()
        super.onDetachedFromWindow()
    }

    private fun startTouch(event: MotionEvent) {
        // A touch that ended without UP or CANCEL mustn't leave the content mid-swipe.
        if (reporting) listener?.onSwipeCancel()
        endTouch()
        pointerId = event.getPointerId(0)
        downX = event.x
        downY = event.y
        velocityTracker = VelocityTracker.obtain()
    }

    private fun checkForSwipe(event: MotionEvent) {
        val index = event.findPointerIndex(pointerId)
        if (index < 0) return
        val x = event.getX(index)
        val dx = x - downX
        val dy = event.getY(index) - downY
        if (abs(dx) <= touchSlop && abs(dy) <= touchSlop) return
        if (abs(dx) <= abs(dy)) {
            settled = true
            return
        }
        // A sideways drag is never a tap, so this layout takes it even when the listener
        // refuses it (during a committed swipe's slide-out); a refused one is swallowed.
        swiping = true
        reporting = listener?.onSwipeStart() == true
        swipeStartX = x
        parent?.requestDisallowInterceptTouchEvent(true)
        cancelOwnPress(event)
    }

    // The first finger lifting while another stays down ends the swipe as a cancel (it springs
    // back); the rest of the touch is then swallowed, or left to View's tap handling if no swipe
    // had started.
    private fun onPointerUp(event: MotionEvent) {
        if (event.getPointerId(event.actionIndex) != pointerId) return
        if (reporting) listener?.onSwipeCancel()
        reporting = false
        settled = true
    }

    private fun firstFingerX(event: MotionEvent): Float? {
        val index = event.findPointerIndex(pointerId)
        return if (index < 0) null else event.getX(index)
    }

    private fun release(x: Float) {
        val tracker = velocityTracker
        tracker?.computeCurrentVelocity(VELOCITY_UNITS_PX_PER_SEC)
        listener?.onSwipeRelease(x - swipeStartX, tracker?.getXVelocity(pointerId) ?: 0f)
    }

    // A swipe isn't a tap: clears the pressed state and pending click of this layout's own tap.
    private fun cancelOwnPress(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        super.onTouchEvent(cancel)
        cancel.recycle()
    }

    private fun endTouch() {
        swiping = false
        settled = false
        reporting = false
        pointerId = MotionEvent.INVALID_POINTER_ID
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
