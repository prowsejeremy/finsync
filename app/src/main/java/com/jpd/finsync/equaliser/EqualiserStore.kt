package com.jpd.finsync.equaliser

import android.content.Context
import android.content.SharedPreferences

private const val PREFS_NAME = "equaliser"
private const val KEY_ENABLED = "enabled"
private const val KEY_PRESET = "preset"
private const val KEY_CUSTOM_GAINS = "custom_gains"
private const val KEY_SAVED_PRESETS = "saved_presets"

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

    fun load(): EqSettings {
        val savedPresets = parseSavedPresets(prefs.getString(KEY_SAVED_PRESETS, null))
        return EqSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, EqSettings.DEFAULT.enabled),
            choice = EqChoice.fromKey(prefs.getString(KEY_PRESET, null), savedPresets),
            customGainsDb = parseCustomGains(prefs.getString(KEY_CUSTOM_GAINS, null)),
            savedPresets = savedPresets
        )
    }

    // Every key at once, so a listener never reads a choice without the preset it names. The
    // listener only hears keys whose value changed, so an unchanged list costs nothing.
    fun save(settings: EqSettings) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, settings.enabled)
            .putString(KEY_PRESET, settings.choice.key)
            .putString(KEY_CUSTOM_GAINS, formatCustomGains(settings.customGainsDb))
            .putString(KEY_SAVED_PRESETS, formatSavedPresets(settings.savedPresets))
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
