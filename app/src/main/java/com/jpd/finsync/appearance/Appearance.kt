package com.jpd.finsync.appearance

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// The Appearance choices and the icon rule. Plain Kotlin, no Android imports (spec
// "Portability"), so this can move to Kotlin Multiplatform or be rewritten in Swift.

/** Dark, Light or System (spec "Behaviour"). [key] is what's saved. */
enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light"),
    SYSTEM("system");

    companion object {
        /** The mode saved as [key]; DARK, the default, for null or a value it doesn't know. */
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DARK
    }
}

/** The five accents, in the Appearance screen's order. [key] is what's saved. */
enum class Accent(val key: String) {
    GREEN("green"),
    BLUE("blue"),
    PURPLE("purple"),
    PINK("pink"),
    RED("red");

    companion object {
        /** The accent saved as [key]; GREEN, the default, for null or a value it doesn't know. */
        fun fromKey(key: String?): Accent = entries.firstOrNull { it.key == key } ?: GREEN
    }
}

/**
 * The icon rule (spec "Accents"): a label or icon drawn on [accentArgb] uses black or white,
 * whichever has the higher WCAG contrast ratio with it. Ties go to black.
 */
fun onAccent(accentArgb: Long): Long {
    val onBlack = contrastRatio(accentArgb, Palette.ICON_BLACK)
    val onWhite = contrastRatio(accentArgb, Palette.ICON_WHITE)
    return if (onBlack >= onWhite) Palette.ICON_BLACK else Palette.ICON_WHITE
}

/** The WCAG 2 contrast ratio of two opaque ARGB colours, from 1 to 21. */
fun contrastRatio(first: Long, second: Long): Double {
    val firstLuminance = relativeLuminance(first)
    val secondLuminance = relativeLuminance(second)
    val lighter = max(firstLuminance, secondLuminance)
    val darker = min(firstLuminance, secondLuminance)
    return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
}

// WCAG 2 relative luminance of an ARGB colour (its alpha is ignored).
private fun relativeLuminance(argb: Long): Double {
    val red = linearChannel((argb shr RED_SHIFT) and CHANNEL_MASK)
    val green = linearChannel((argb shr GREEN_SHIFT) and CHANNEL_MASK)
    val blue = linearChannel(argb and CHANNEL_MASK)
    return RED_WEIGHT * red + GREEN_WEIGHT * green + BLUE_WEIGHT * blue
}

// One sRGB channel (0–255) on the linear scale WCAG weighs.
private fun linearChannel(value: Long): Double {
    val channel = value / CHANNEL_MAX
    return if (channel <= SRGB_LINEAR_LIMIT) {
        channel / SRGB_LINEAR_SLOPE
    } else {
        ((channel + SRGB_OFFSET) / (1 + SRGB_OFFSET)).pow(SRGB_GAMMA)
    }
}

private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFFL
private const val CHANNEL_MAX = 255.0
private const val SRGB_LINEAR_LIMIT = 0.03928
private const val SRGB_LINEAR_SLOPE = 12.92
private const val SRGB_OFFSET = 0.055
private const val SRGB_GAMMA = 2.4
private const val RED_WEIGHT = 0.2126
private const val GREEN_WEIGHT = 0.7152
private const val BLUE_WEIGHT = 0.0722
private const val LUMINANCE_OFFSET = 0.05
