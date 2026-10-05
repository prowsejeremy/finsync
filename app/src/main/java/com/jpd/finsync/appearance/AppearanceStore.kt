package com.jpd.finsync.appearance

import android.content.Context

private const val SETTINGS_PREFS = "settings"
private const val THEME_MODE_KEY = "theme_mode"
private const val ACCENT_KEY = "accent"

/**
 * The Appearance choices, in the settings prefs (spec "Saved settings"). Values name the choice,
 * not a colour, so later palette changes need no migration; a missing or unknown value reads as
 * the default (Dark, Green).
 */
class AppearanceStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    fun themeMode(): ThemeMode = ThemeMode.fromKey(prefs.getString(THEME_MODE_KEY, null))

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(THEME_MODE_KEY, mode.key).apply()
    }

    fun accent(): Accent = Accent.fromKey(prefs.getString(ACCENT_KEY, null))

    fun setAccent(accent: Accent) {
        prefs.edit().putString(ACCENT_KEY, accent.key).apply()
    }
}
