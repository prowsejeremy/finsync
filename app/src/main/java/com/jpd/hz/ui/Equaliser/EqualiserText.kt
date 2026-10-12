package com.jpd.hz.ui

import android.content.res.Resources
import androidx.annotation.StringRes
import com.jpd.hz.R
import com.jpd.hz.equaliser.EqChoice
import com.jpd.hz.equaliser.EqPreset
import com.jpd.hz.equaliser.EqSettings
import com.jpd.hz.equaliser.bandLabel

// The equaliser's words on screen. The rules behind them stay plain Kotlin, in EqFormat.kt and
// the equaliser package (spec "Portable core").

/** "Flat", "Bass boost" … "Spoken word", or "Custom". */
@get:StringRes
val EqPreset.titleRes: Int
    get() = when (this) {
        EqPreset.FLAT -> R.string.equaliser_preset_flat
        EqPreset.BASS_BOOST -> R.string.equaliser_preset_bass_boost
        EqPreset.TREBLE_BOOST -> R.string.equaliser_preset_treble_boost
        EqPreset.VOCAL -> R.string.equaliser_preset_vocal
        EqPreset.ROCK -> R.string.equaliser_preset_rock
        EqPreset.POP -> R.string.equaliser_preset_pop
        EqPreset.JAZZ -> R.string.equaliser_preset_jazz
        EqPreset.CLASSICAL -> R.string.equaliser_preset_classical
        EqPreset.SPOKEN_WORD -> R.string.equaliser_preset_spoken_word
        EqPreset.CUSTOM -> R.string.equaliser_preset_custom
    }

/** Every built-in preset's name, Custom included, for the name rules (spec "Name rules"). */
fun Resources.builtInTitles(): List<String> = EqPreset.entries.map { getString(it.titleRes) }

/** The chosen preset's name: a built-in's title, "Custom", or a saved preset's own name. */
fun Resources.choiceTitle(settings: EqSettings): String = when (val choice = settings.choice) {
    is EqChoice.Preset -> getString(choice.preset.titleRes)
    is EqChoice.Saved -> checkNotNull(settings.savedPreset(choice.id)).name
}

/** A band's label: "31 Hz" … "16 kHz". */
fun Resources.bandLabelText(band: Int): String {
    val label = bandLabel(band)
    val format = if (label.kilohertz) R.string.equaliser_band_khz else R.string.equaliser_band_hz
    return getString(format, label.value)
}

/** A band's gain: "+6.5 dB", "0.0 dB" or "−3.0 dB". */
fun Resources.gainText(gainDb: Float): String =
    getString(R.string.equaliser_gain, signedGainText(gainDb))

/** The Settings row's summary: "Off", or "On · Bass boost" or "On · Night drive". */
fun Resources.equaliserSummary(settings: EqSettings): String {
    summaryChoice(settings) ?: return getString(R.string.equaliser_summary_off)
    val on = getString(R.string.equaliser_summary_on)
    return joinWithDots(listOf(on, choiceTitle(settings)))
}
