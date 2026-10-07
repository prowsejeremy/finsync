package com.jpd.hz.ui

import android.content.Context
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.R

// A typical phone on its side: 384–411 dp wide, less the status and navigation bars. The
// tests' qualifiers pin mdpi, so a dp is a pixel.
const val LANDSCAPE_WIDTH_DP = 760
const val LANDSCAPE_HEIGHT_DP = 340

// Shorter than anything should need: a small phone with three-button navigation, or a large
// font.
const val CRAMPED_HEIGHT_DP = 260

fun themedContext(): Context =
    ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_Hz)

/** Lays [root] out at exactly [widthPx] by [heightPx], as a screen would. */
fun layOut(root: View, widthPx: Int, heightPx: Int) {
    root.measure(
        View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
    )
    root.layout(0, 0, widthPx, heightPx)
}

/** The nearest view above this one that can scroll further down, if any. */
fun View.scrollingAncestor(): ViewGroup? =
    generateSequence(parent as? ViewGroup) { it.parent as? ViewGroup }
        .firstOrNull { it.canScrollVertically(1) }

/**
 * Scrolls a ScrollView as far down as it goes. Its scrollTo clamps to the content, but adds
 * before it compares, so a huge offset like Int.MAX_VALUE overflows past the clamp.
 */
fun ViewGroup.scrollToEnd() = scrollTo(0, getChildAt(0).height)

/** Where this view shows within [root], after every scroll above it. */
fun View.frameIn(root: View): Rect {
    var x = left
    var y = top
    var parentView = parent as View
    while (parentView !== root) {
        x += parentView.left - parentView.scrollX
        y += parentView.top - parentView.scrollY
        parentView = parentView.parent as View
    }
    x -= root.scrollX
    y -= root.scrollY
    return Rect(x, y, x + width, y + height)
}
