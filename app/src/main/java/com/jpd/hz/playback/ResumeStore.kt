package com.jpd.hz.playback

import android.content.Context

private const val PREFS_NAME = "playback"
private const val KEY_STATE = "resume_state"

/** The saved queue, in the "playback" prefs file. */
class ResumeStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): ResumeState? = decodeResumeState(prefs.getString(KEY_STATE, null))

    /** Saving null (an empty queue) clears the saved queue. */
    fun save(state: ResumeState?) {
        if (state == null) {
            clear()
        } else {
            prefs.edit().putString(KEY_STATE, encodeResumeState(state)).apply()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_STATE).apply()
    }
}
