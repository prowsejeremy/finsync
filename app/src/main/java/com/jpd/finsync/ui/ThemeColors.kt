package com.jpd.finsync.ui

import android.content.Context
import androidx.annotation.ColorInt
import com.google.android.material.color.MaterialColors

// The accent is whatever the theme says (Theme.Finsync's Green, or an Appearance overlay), so
// code reads it here instead of naming a colour resource (spec "Android wiring").

/** The current accent: the theme's colorPrimary. */
@ColorInt
fun Context.accentColor(): Int =
    MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, "accentColor")

/** Black or white, whichever reads on the accent: the theme's colorOnPrimary. */
@ColorInt
fun Context.onAccentColor(): Int = MaterialColors.getColor(
    this, com.google.android.material.R.attr.colorOnPrimary, "onAccentColor"
)
