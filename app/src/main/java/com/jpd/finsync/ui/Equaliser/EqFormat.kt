package com.jpd.finsync.ui

import com.jpd.finsync.equaliser.EqPreset
import com.jpd.finsync.equaliser.EqSettings
import com.jpd.finsync.equaliser.GAIN_STEPS_PER_DB
import kotlin.math.abs
import kotlin.math.roundToInt

// A real minus sign (U+2212), as the spec asks: a hyphen reads as a dash.
private const val MINUS_SIGN = '−'

/**
 * A band's gain without its unit, always to one decimal: "+6.5", "0.0" or "−3.0" (spec
 * "Equaliser"). Built from whole tenths, so the decimal point is always a point.
 */
fun signedGainText(gainDb: Float): String {
    val tenths = (gainDb * GAIN_STEPS_PER_DB).roundToInt()
    val magnitude = abs(tenths)
    val number = "${magnitude / GAIN_STEPS_PER_DB}.${magnitude % GAIN_STEPS_PER_DB}"
    return when {
        tenths > 0 -> "+$number"
        tenths < 0 -> "$MINUS_SIGN$number"
        else -> number
    }
}

/** The preset the Settings row names after "On · ", or null while the row reads "Off". */
fun summaryPreset(settings: EqSettings): EqPreset? = settings.preset.takeIf { settings.enabled }
