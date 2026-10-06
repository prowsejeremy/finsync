package com.jpd.hz

import android.app.Application
import com.jpd.hz.appearance.AppearanceStore
import com.jpd.hz.appearance.applyThemeMode

class Hz : Application() {
    override fun onCreate() {
        super.onCreate()
        // The saved mode, set before any activity starts so the app's screens never show the
        // other mode first (spec "No flash on launch"). On Android 12+ it also tells Android,
        // which keeps it for the launch screen it draws before this runs.
        applyThemeMode(AppearanceStore(this).themeMode())
    }
}
