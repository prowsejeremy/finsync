package com.jpd.hz.playback

import android.content.Context

private const val PREFS_NAME = "playback"
private const val KEY_BOOK_SPEED = "book_speed"

/**
 * The one speed every book plays at, kept across restarts. It sits beside the resume state in
 * the "playback" prefs; logout clears only the resume state, so the speed stays (spec "Logout").
 */
class BookSpeedStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Float = nearestBookSpeed(prefs.getFloat(KEY_BOOK_SPEED, DEFAULT_BOOK_SPEED))

    fun save(speed: Float) {
        prefs.edit().putFloat(KEY_BOOK_SPEED, speed).apply()
    }
}
