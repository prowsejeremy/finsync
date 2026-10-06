package com.jpd.hz.appearance

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.jpd.hz.R

// The Android side of Appearance: night modes, theme overlays and names. Appearance.kt stays
// plain Kotlin (spec "Portability").

/** The AppCompat night mode that shows this choice. */
val ThemeMode.nightMode: Int
    get() = when (this) {
        ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

// The same choice in UiModeManager's constants, which differ: AppCompat's FOLLOW_SYSTEM is -1,
// and UiModeManager's AUTO (0) is the one that follows the phone.
private val ThemeMode.uiModeNightMode: Int
    get() = when (this) {
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
    }

/**
 * Shows this mode. AppCompat draws the app's screens in it on every Android version, recreating
 * the started activities when it changes. Android draws the launch screen itself, before any app
 * code runs, in the phone's mode; from Android 12 it keeps an app's own mode for that, so later
 * cold launches open in this mode too (spec "No flash on launch").
 */
fun Context.applyThemeMode(mode: ThemeMode) {
    AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(UiModeManager::class.java).setApplicationNightMode(mode.uiModeNightMode)
    }
}

/** "Dark", "Light" or "System". */
@get:StringRes
val ThemeMode.nameRes: Int
    get() = when (this) {
        ThemeMode.DARK -> R.string.appearance_mode_dark
        ThemeMode.LIGHT -> R.string.appearance_mode_light
        ThemeMode.SYSTEM -> R.string.appearance_mode_system
    }

/** The overlay that sets this accent's colorPrimary and colorOnPrimary (themes.xml). */
@get:StyleRes
val Accent.overlayRes: Int
    get() = when (this) {
        Accent.GREEN -> R.style.ThemeOverlay_Hz_Accent_Green
        Accent.BLUE -> R.style.ThemeOverlay_Hz_Accent_Blue
        Accent.PURPLE -> R.style.ThemeOverlay_Hz_Accent_Purple
        Accent.PINK -> R.style.ThemeOverlay_Hz_Accent_Pink
        Accent.RED -> R.style.ThemeOverlay_Hz_Accent_Red
    }

/** "Green", "Blue", "Purple", "Pink" or "Red". */
@get:StringRes
val Accent.nameRes: Int
    get() = when (this) {
        Accent.GREEN -> R.string.appearance_accent_green
        Accent.BLUE -> R.string.appearance_accent_blue
        Accent.PURPLE -> R.string.appearance_accent_purple
        Accent.PINK -> R.string.appearance_accent_pink
        Accent.RED -> R.string.appearance_accent_red
    }

/**
 * Lays the saved accent's overlay over this activity's theme. Call it in onCreate after
 * super.onCreate() and before anything is inflated: AppCompat's own onCreate can re-apply the
 * base theme, and views and sheets made later copy the theme as it is then.
 */
fun Activity.applyAccentOverlay() {
    theme.applyStyle(AppearanceStore(this).accent().overlayRes, true)
}
