package com.jpd.finsync

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class Finsync : Application() {
    override fun onCreate() {
        super.onCreate()
        // The theme is DayNight. Dark stays pinned until the Appearance setting can change it,
        // so the app looks as it did; it's set before any activity starts, so nothing flashes.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
