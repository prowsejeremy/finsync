package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.jpd.hz.appearance.Accent
import com.jpd.hz.appearance.AppearanceStore
import com.jpd.hz.appearance.ThemeMode

/** Settings' own choices: Appearance (spec "Settings screens"). Each platform page has its own. */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val appearance = AppearanceStore(app)

    // Appearance's choices (spec "Settings screens"); AppearanceFragment applies them.

    fun themeMode(): ThemeMode = appearance.themeMode()

    fun setThemeMode(mode: ThemeMode) = appearance.setThemeMode(mode)

    fun accent(): Accent = appearance.accent()

    fun setAccent(accent: Accent) = appearance.setAccent(accent)
}
