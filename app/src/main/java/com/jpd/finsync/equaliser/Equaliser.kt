package com.jpd.finsync.equaliser

import kotlin.math.pow
import kotlin.math.roundToInt

const val BAND_COUNT = 10
const val MIN_GAIN_DB = -12f
const val MAX_GAIN_DB = 12f
// The sliders move in tenths of a decibel (the user's change, 2026-10-05).
const val GAIN_STEPS_PER_DB = 10
// Octave-spaced bands, each an octave wide: a standard 10-band graphic EQ.
const val BANDWIDTH_OCTAVES = 1f

private const val REFERENCE_BAND = 5
private const val REFERENCE_HZ = 1000f
private const val HZ_PER_KHZ = 1000f
private const val GAIN_SEPARATOR = ","

/** The band centres in Hz: octaves from 31.25 Hz to 16 kHz, with 1 kHz at index 5 (spec). */
val BAND_CENTRES_HZ: List<Float> =
    List(BAND_COUNT) { band -> REFERENCE_HZ * 2f.pow(band - REFERENCE_BAND) }

private val FLAT_GAINS: List<Float> = List(BAND_COUNT) { 0f }

/** A band's label: [value] Hz, or [value] kHz when [kilohertz] ("31 Hz", "16 kHz"). */
data class BandLabel(val value: Int, val kilohertz: Boolean)

/** The label for [band], with the centre's fraction dropped, so 31.25 Hz reads "31 Hz". */
fun bandLabel(band: Int): BandLabel {
    val centreHz = BAND_CENTRES_HZ[band]
    return when {
        centreHz < HZ_PER_KHZ -> BandLabel(centreHz.toInt(), kilohertz = false)
        else -> BandLabel((centreHz / HZ_PER_KHZ).toInt(), kilohertz = true)
    }
}

/**
 * [gainDb] within the slider's range, at the nearest tenth. Every gain passes through here, so
 * equal settings compare equal however a slider's float arithmetic reached them.
 */
fun clampGain(gainDb: Float): Float {
    val steps = (gainDb.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) * GAIN_STEPS_PER_DB).roundToInt()
    return steps / GAIN_STEPS_PER_DB.toFloat()
}

// The presets are whole decibels, written as the spec's table.
private fun wholeDb(vararg gainsDb: Int): List<Float> = gainsDb.map(Int::toFloat)

/** The built-in curves in the screen's order, then Custom (spec "Presets"). Keys are saved. */
enum class EqPreset(val key: String, val gainsDb: List<Float>?) {
    FLAT("flat", FLAT_GAINS),
    BASS_BOOST("bass_boost", wholeDb(6, 5, 4, 2, 0, 0, 0, 0, 0, 0)),
    TREBLE_BOOST("treble_boost", wholeDb(0, 0, 0, 0, 0, 1, 2, 4, 5, 6)),
    VOCAL("vocal", wholeDb(-3, -2, -1, 1, 3, 4, 4, 3, 1, 0)),
    ROCK("rock", wholeDb(5, 4, 2, 0, -1, -1, 1, 3, 4, 5)),
    POP("pop", wholeDb(-1, 1, 3, 4, 3, 1, 0, -1, -1, -2)),
    JAZZ("jazz", wholeDb(3, 2, 1, 2, -1, -1, 0, 1, 2, 3)),
    CLASSICAL("classical", wholeDb(4, 3, 2, 1, 0, 0, 0, 0, 1, 2)),
    SPOKEN_WORD("spoken_word", wholeDb(-6, -4, -2, 0, 2, 3, 3, 2, 0, -2)),
    /** The user's own curve, kept in [EqSettings.customGainsDb]. */
    CUSTOM("custom", null);

    companion object {
        /** Unknown or missing keys read as [FLAT]. */
        fun fromKey(key: String?): EqPreset = entries.firstOrNull { it.key == key } ?: FLAT
    }
}

/**
 * What the equaliser plays (spec "Saved settings"). Plain Kotlin, so the rules are tested on the
 * JVM and can move to an iOS port.
 */
data class EqSettings(
    val enabled: Boolean,
    val preset: EqPreset,
    /** The Custom slot: ten gains, kept while a built-in preset is chosen. */
    val customGainsDb: List<Float>
) {
    init {
        require(customGainsDb.size == BAND_COUNT) { "Expected $BAND_COUNT gains" }
    }

    /** The curve shown and played: the preset's, or the Custom slot's. */
    val gainsDb: List<Float> get() = preset.gainsDb ?: customGainsDb

    /**
     * Moving a band turns the EQ on and makes the shown curve, with [band] changed, the new
     * Custom slot.
     */
    fun withBand(band: Int, gainDb: Float): EqSettings {
        val gains = gainsDb.toMutableList()
        gains[band] = clampGain(gainDb)
        return EqSettings(enabled = true, preset = EqPreset.CUSTOM, customGainsDb = gains)
    }

    /** Choosing a preset turns the EQ on. The Custom slot stays for when Custom is chosen again. */
    fun withPreset(preset: EqPreset): EqSettings = copy(enabled = true, preset = preset)

    fun withEnabled(enabled: Boolean): EqSettings = copy(enabled = enabled)

    /** The gains for the engine, or null to bypass it: off, or every band at 0 dB. */
    fun activeGainsDb(): List<Float>? =
        gainsDb.takeIf { enabled && it.any { gainDb -> gainDb != 0f } }

    companion object {
        val DEFAULT =
            EqSettings(enabled = false, preset = EqPreset.FLAT, customGainsDb = FLAT_GAINS)
    }
}

/** The Custom slot as saved in "custom_gains": ten comma-separated decimals, "6.5,-3.0,…". */
fun formatCustomGains(gainsDb: List<Float>): String = gainsDb.joinToString(GAIN_SEPARATOR)

/**
 * Reads a saved "custom_gains" value. Anything but exactly ten finite numbers reads as flat;
 * whole numbers saved before the tenths step still read. Each gain goes through [clampGain].
 */
fun parseCustomGains(value: String?): List<Float> {
    val parts = value?.split(GAIN_SEPARATOR) ?: return FLAT_GAINS
    val gains = parts.mapNotNull { part -> part.trim().toFloatOrNull()?.takeIf { it.isFinite() } }
    if (parts.size != BAND_COUNT || gains.size != BAND_COUNT) return FLAT_GAINS
    return gains.map(::clampGain)
}
