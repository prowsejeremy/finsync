package com.jpd.hz.adapter.service

import android.content.Context

private const val PREFS = "settings"
private const val KEY_PREFIX = "auto_sync"
/** The Auto-sync screen's choice when no schedule is set. */
const val AUTO_SYNC_OFF = "disabled"

/**
 * Each connection's Auto-sync choice, `auto_sync:<connectionId>`: `disabled`, or an interval in
 * hours (spec "Saved settings"), with its schedule kept to match.
 */
class AutoSync(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun interval(connectionId: String): String =
        prefs.getString(keyOf(connectionId), AUTO_SYNC_OFF) ?: AUTO_SYNC_OFF

    /** Saves [interval] and schedules to match it. */
    fun set(connectionId: String, interval: String, needsNetwork: Boolean) {
        prefs.edit().putString(keyOf(connectionId), interval).apply()
        val hours = interval.toLongOrNull()
        // KEEP leaves an existing schedule alone, so a new interval cancels the old one first.
        SyncScheduler.cancel(appContext, connectionId)
        if (hours != null) SyncScheduler.schedule(appContext, connectionId, hours, needsNetwork)
    }

    /** Sign-out's step: the schedule goes, and the screen reads Disabled. */
    fun turnOff(connectionId: String) {
        prefs.edit().putString(keyOf(connectionId), AUTO_SYNC_OFF).apply()
        SyncScheduler.cancel(appContext, connectionId)
    }

    private fun keyOf(connectionId: String) = "$KEY_PREFIX:$connectionId"
}
