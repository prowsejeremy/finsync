package com.jpd.hz.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs

private const val VELOCITY_UNITS_PX_PER_SEC = 1_000
// canScrollVertically's direction for "has more above".
private const val SCROLL_UP = -1

/**
 * Turns a downward drag over its content into a drag to close (spec "Which drags close"). It
 * takes a touch over once the finger has moved past the system touch slop, downward and at least
 * as far down as sideways: the exact complement of [HorizontalSwipeLayout]'s rule, so the two
 * never both take a drag. A button under the finger then gets ACTION_CANCEL, so it doesn't click.
 *
 * It checks each move before its children see it, so it wins over them, except where a child
 * already has the touch (it called requestDisallowInterceptTouchEvent, as the seek bar does on
 * touch down) or a view under the finger can scroll up (it scrolls to its top first). It moves
 * nothing itself; the [Listener] does. Only the first finger counts: if it lifts while another
 * stays down, the drag is cancelled and the rest is ignored.
 */
class SwipeDownLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** Hears one drag from start to end. Distances and speeds are pixels; positive is down. */
    interface Listener {
        /**
         * A downward drag passed the slop. Return false to have it ignored; it's still taken, so
         * nothing under it clicks.
         */
        fun onDragStart(): Boolean

        /** The finger is [dyPx] below where the drag started. */
        fun onDrag(dyPx: Float)

        /** The finger lifted [dyPx] below the start, moving at [velocityPxPerSec]. */
        fun onDragRelease(dyPx: Float, velocityPxPerSec: Float)

        /** The touch was taken away, or this layout left the window, mid-drag. */
        fun onDragCancel()
    }

    var listener: Listener? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var velocityTracker: VelocityTracker? = null
    // The first finger down; other fingers are ignored, so they can't make the content jump.
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var dragStartY = 0f
    private var dragging = false
    // Past the slop without becoming a drag, or a child took the touch: it's left alone.
    private var settled = false
    // The listener accepted this drag, so it hears it; a refused one is swallowed.
    private var reporting = false

    // Every event of a touch passes here once, whichever view ends up handling it.
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> startTouch(event)
            MotionEvent.ACTION_MOVE -> if (!dragging && !settled) checkForDrag(event)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(event)
        }
        velocityTracker?.addMovement(event)
        val handled = super.dispatchTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) endTouch()
        return handled
    }

    // True once dragging: the child under the finger gets ACTION_CANCEL, and the rest comes here.
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = dragging

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!dragging) return super.onTouchEvent(event)
        // A refused or dropped drag is swallowed: it never reaches the controls.
        if (!reporting) return true
        if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            listener?.onDragCancel()
            return true
        }
        val y = firstFingerY(event) ?: return true
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> listener?.onDrag(y - dragStartY)
            MotionEvent.ACTION_UP -> release(y)
        }
        return true
    }

    // Never clickable itself; overridden so lint sees onTouchEvent leaves clicks alone.
    override fun performClick(): Boolean = super.performClick()

    // A child that takes the touch (the seek bar on touch down, the track swipe once sideways, a
    // ScrollView once scrolling) keeps it.
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (disallowIntercept && !dragging) settled = true
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onDetachedFromWindow() {
        if (reporting) listener?.onDragCancel()
        endTouch()
        super.onDetachedFromWindow()
    }

    private fun startTouch(event: MotionEvent) {
        // A touch that ended without UP or CANCEL mustn't leave the content mid-drag.
        if (reporting) listener?.onDragCancel()
        endTouch()
        pointerId = event.getPointerId(0)
        downX = event.x
        downY = event.y
        velocityTracker = VelocityTracker.obtain()
    }

    private fun checkForDrag(event: MotionEvent) {
        val index = event.findPointerIndex(pointerId)
        if (index < 0) return
        val y = event.getY(index)
        val dx = event.getX(index) - downX
        val dy = y - downY
        if (abs(dx) <= touchSlop && abs(dy) <= touchSlop) return
        // Sideways is HorizontalSwipeLayout's; up, or down through a view that can scroll up,
        // belongs to the view under the finger.
        if (dy <= 0f || abs(dy) < abs(dx) || canScrollUpAt(this, downX, downY)) {
            settled = true
            return
        }
        dragging = true
        reporting = listener?.onDragStart() == true
        dragStartY = y
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    // Whether a view under ([x], [y]), in [group]'s coordinates, can scroll up. Ignores scale and
    // rotation, which the Player doesn't use.
    private fun canScrollUpAt(group: ViewGroup, x: Float, y: Float): Boolean {
        for (index in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(index)
            val childX = x + group.scrollX - child.left - child.translationX
            val childY = y + group.scrollY - child.top - child.translationY
            val under = child.visibility == View.VISIBLE &&
                childX >= 0f && childX < child.width && childY >= 0f && childY < child.height
            if (!under) continue
            if (child.canScrollVertically(SCROLL_UP)) return true
            if (child is ViewGroup && canScrollUpAt(child, childX, childY)) return true
        }
        return false
    }

    // The first finger lifting while another stays down ends the drag as a cancel (it springs
    // back); the rest of the touch is then swallowed, or left to the children if no drag had
    // started.
    private fun onPointerUp(event: MotionEvent) {
        if (event.getPointerId(event.actionIndex) != pointerId) return
        if (reporting) listener?.onDragCancel()
        reporting = false
        settled = true
    }

    private fun firstFingerY(event: MotionEvent): Float? {
        val index = event.findPointerIndex(pointerId)
        return if (index < 0) null else event.getY(index)
    }

    private fun release(y: Float) {
        val tracker = velocityTracker
        tracker?.computeCurrentVelocity(VELOCITY_UNITS_PX_PER_SEC)
        listener?.onDragRelease(y - dragStartY, tracker?.getYVelocity(pointerId) ?: 0f)
    }

    private fun endTouch() {
        dragging = false
        settled = false
        reporting = false
        pointerId = MotionEvent.INVALID_POINTER_ID
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
