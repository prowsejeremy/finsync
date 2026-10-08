package com.jpd.hz

import android.app.Application
import android.content.Context
import com.jpd.hz.appearance.AppearanceStore
import com.jpd.hz.appearance.applyThemeMode
import com.jpd.hz.library.scan.LibraryScanner

class Hz : Application() {
    override fun onCreate() {
        super.onCreate()
        // The saved mode, set before any activity starts so the app's screens never show the
        // other mode first (spec "No flash on launch"). On Android 12+ it also tells Android,
        // which keeps it for the launch screen it draws before this runs.
        applyThemeMode(AppearanceStore(this).themeMode())
    }
}

/**
 * The shell's rescan hook (D1): an adapter calls it when a sync ends, whatever the result. It
 * carries no data; the player reads what's in the Library folder.
 */
fun requestLibraryRescan(context: Context) {
    LibraryScanner.get(context).requestScan()
}
