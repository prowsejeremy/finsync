package com.jpd.finsync.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.HorizontalScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * The Player's one-line title (spec "Player title"): a HorizontalScrollView round the
 * single-line TextView declared inside it in the layout. A title that fits sits centred and
 * still. A longer one holds, scrolls until its end shows, holds, fades back to its start and
 * repeats. With "Remove animations" on, it's held to the view's width and ends in "…" instead.
 */
class ScrollingTitleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    private val density = resources.displayMetrics.density
    private lateinit var textView: TextView
    private var scrollAnimator: ValueAnimator? = null
    private var overflowPx = 0
    private var scrollMs = 0L
    // Read at each measure, so turning "Remove animations" on or off applies at the next layout.
    private var scrolls = true
    // Set by new text, a new width or being shown again: the next layout restarts the cycle.
    private var cycleStale = true
    private val startScroll = Runnable { scrollToEnd() }
    private val fadeBack = Runnable { fadeToStart() }

    init {
        isFillViewport = true
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        // Fades only the side with hidden text (HorizontalScrollView's fading edge strengths).
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength((TITLE_FADE_EDGE_DP * density).roundToInt())
        isFocusable = false
        // TalkBack reads the TextView's whole title; this view's scroll actions would fight it.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        textView = checkNotNull(getChildAt(0) as? TextView) {
            "ScrollingTitleView needs one TextView inside it"
        }
    }

    /**
     * Shows [title]. The same text again changes nothing, so the cycle isn't restarted by the
     * Player's play, pause and position updates (spec "Restarts").
     */
    fun setTitle(title: CharSequence) {
        if (TextUtils.equals(textView.text, title)) return
        // The TextView is wrap_content, so new text requests a layout, which restarts the cycle.
        cycleStale = true
        textView.text = title
    }

    // Never reacts to a finger (spec): touches go to whatever is behind it.
    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        scrolls = ValueAnimator.areAnimatorsEnabled()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun measureChildWithMargins(
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ) {
        if (scrolls) {
            super.measureChildWithMargins(
                child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed
            )
            return
        }
        // "Remove animations" is on: the title gets the view's width, so it ends in "…".
        val params = child.layoutParams as ViewGroup.MarginLayoutParams
        val horizontalPx = paddingLeft + paddingRight + params.leftMargin + params.rightMargin
        val verticalPx = paddingTop + paddingBottom + params.topMargin + params.bottomMargin
        val widthPx = View.MeasureSpec.getSize(parentWidthMeasureSpec) - horizontalPx - widthUsed
        child.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx.coerceAtLeast(0), View.MeasureSpec.EXACTLY),
            ViewGroup.getChildMeasureSpec(
                parentHeightMeasureSpec, verticalPx + heightUsed, params.height
            )
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) cycleStale = true
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (cycleStale) {
            cycleStale = false
            restartCycle()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) {
            // Shown again: from the first hold, after a layout that also rechecks the setting.
            cycleStale = true
            requestLayout()
        } else {
            stopCycle()
        }
    }

    override fun onDetachedFromWindow() {
        stopCycle()
        super.onDetachedFromWindow()
    }

    private fun restartCycle() {
        stopCycle()
        if (!scrolls || windowVisibility != View.VISIBLE) return
        overflowPx = titleOverflowPx(textView.width, width - paddingLeft - paddingRight)
        // A title that fits stays centred and still.
        if (overflowPx == 0) return
        scrollMs = scrollDurationMs(overflowPx, TITLE_SCROLL_DP_PER_SEC * density)
        // Holds wait on the view's handler, not an animator, so nothing ticks while it holds.
        postDelayed(startScroll, TITLE_HOLD_START_MS)
    }

    private fun scrollToEnd() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            // Turned off mid-cycle: the next layout switches to "…".
            cycleStale = true
            requestLayout()
            return
        }
        val animator = ValueAnimator.ofInt(0, overflowPx)
        animator.duration = scrollMs
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // stopCycle clears scrollAnimator before cancelling, so a stopped scroll ends here.
                if (scrollAnimator === animation) postDelayed(fadeBack, TITLE_HOLD_END_MS)
            }
        })
        scrollAnimator = animator
        animator.start()
    }

    private fun fadeToStart() {
        textView.animate().alpha(0f).setDuration(TITLE_FADE_MS).withEndAction {
            scrollTo(0, 0)
            textView.animate().alpha(1f).setDuration(TITLE_FADE_MS).withEndAction {
                postDelayed(startScroll, TITLE_HOLD_START_MS)
            }
        }
    }

    // Cancels every step of the cycle and puts the title back at its start, fully shown.
    private fun stopCycle() {
        removeCallbacks(startScroll)
        removeCallbacks(fadeBack)
        val animator = scrollAnimator
        scrollAnimator = null
        animator?.cancel()
        // A cancelled fade's end action never runs, so it can't post another hold.
        textView.animate().cancel()
        textView.alpha = 1f
        scrollTo(0, 0)
    }
}
