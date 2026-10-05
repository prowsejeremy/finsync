package com.jpd.finsync.equaliser

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "equaliser"
private const val KEY_ENABLED = "enabled"
private const val KEY_PRESET = "preset"
private const val KEY_CUSTOM_GAINS = "custom_gains"

/**
 * The equaliser's saved settings (spec "Saved settings"). They have their own prefs file, so the
 * resume state's saves every 10 s don't wake [listen]'s listener. Logout leaves them, like the
 * book speed.
 */
class EqualiserStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    // SharedPreferences holds listeners weakly, so the store keeps the one it registers.
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun load(): EqSettings = EqSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, EqSettings.DEFAULT.enabled),
        preset = EqPreset.fromKey(prefs.getString(KEY_PRESET, null)),
        customGainsDb = parseCustomGains(prefs.getString(KEY_CUSTOM_GAINS, null))
    )

    // All three keys at once, so a listener never reads a preset without its Custom slot.
    fun save(settings: EqSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_PRESET, settings.preset.key)
            .putString(KEY_CUSTOM_GAINS, formatCustomGains(settings.customGainsDb))
            .apply()
    }

    /**
     * Calls [onChange] with the saved settings after each change, on the main thread (Android
     * runs these listeners there). Replaces any earlier listener.
     */
    fun listen(onChange: (EqSettings) -> Unit) {
        stopListening()
        // Any key, or a null one from clear(), means the settings may have changed.
        val newListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            onChange(load())
        }
        prefs.registerOnSharedPreferenceChangeListener(newListener)
        listener = newListener
    }

    fun stopListening() {
        listener?.let(prefs::unregisterOnSharedPreferenceChangeListener)
        listener = null
    }
}
