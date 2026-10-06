package com.jpd.hz.equaliser

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
// A saved preset's choice is saved as "saved_3". No built-in key starts like this.
private const val SAVED_KEY_PREFIX = "saved_"

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
 * What the preset list has chosen (saved-presets spec "Data"): a built-in curve or Custom, or one
 * of the user's saved presets.
 */
sealed interface EqChoice {

    /** The value saved under "preset". */
    val key: String

    /** A built-in preset, or Custom. */
    data class Preset(val preset: EqPreset) : EqChoice {
        override val key: String get() = preset.key
    }

    /** One of [EqSettings.savedPresets], by its id. */
    data class Saved(val id: Int) : EqChoice {
        override val key: String get() = "$SAVED_KEY_PREFIX$id"
    }

    companion object {
        val CUSTOM: EqChoice = Preset(EqPreset.CUSTOM)

        /**
         * Reads a saved "preset" value. A saved preset's key whose id isn't in [savedPresets]
         * reads as Flat, like any unknown key.
         */
        fun fromKey(key: String?, savedPresets: List<SavedPreset>): EqChoice {
            val savedId = key?.takeIf { it.startsWith(SAVED_KEY_PREFIX) }
                ?.removePrefix(SAVED_KEY_PREFIX)
                ?.toIntOrNull()
            if (savedId != null && savedPresets.any { it.id == savedId }) return Saved(savedId)
            return Preset(EqPreset.fromKey(key))
        }
    }
}

/**
 * What the equaliser plays (spec "Saved settings"). Plain Kotlin, so the rules are tested on the
 * JVM and can move to an iOS port.
 */
data class EqSettings(
    val enabled: Boolean,
    val choice: EqChoice,
    /** The Custom slot: ten gains, kept while another preset is chosen. */
    val customGainsDb: List<Float>,
    /** The user's named presets, in the order they were made. */
    val savedPresets: List<SavedPreset> = emptyList()
) {
    init {
        require(customGainsDb.size == BAND_COUNT) { "Expected $BAND_COUNT gains" }
        require(choice !is EqChoice.Saved || savedPreset(choice.id) != null) {
            "No saved preset for ${choice.key}"
        }
    }

    /** The curve shown and played: the built-in's, the Custom slot's or the saved preset's. */
    val gainsDb: List<Float>
        get() = when (choice) {
            is EqChoice.Preset -> choice.preset.gainsDb ?: customGainsDb
            is EqChoice.Saved -> checkNotNull(savedPreset(choice.id)).gainsDb
        }

    /** The saved preset with [id], or null. */
    fun savedPreset(id: Int): SavedPreset? = savedPresets.firstOrNull { it.id == id }

    /**
     * Moving a band turns the EQ on and makes the shown curve, with [band] changed, the new
     * Custom slot. A saved preset never changes this way.
     */
    fun withBand(band: Int, gainDb: Float): EqSettings {
        val gains = gainsDb.toMutableList()
        gains[band] = clampGain(gainDb)
        return copy(enabled = true, choice = EqChoice.CUSTOM, customGainsDb = gains)
    }

    /** Choosing anything turns the EQ on. The Custom slot stays for when Custom is chosen again. */
    fun withChoice(choice: EqChoice): EqSettings = copy(enabled = true, choice = choice)

    fun withPreset(preset: EqPreset): EqSettings = withChoice(EqChoice.Preset(preset))

    fun withEnabled(enabled: Boolean): EqSettings = copy(enabled = enabled)

    /**
     * Saves the curve on screen as [name] and chooses it, turning the EQ on (spec "The name
     * editor"). A saved preset with that name, ignoring case, takes the curve and keeps its id,
     * name and place; otherwise the new preset goes last. The Custom slot doesn't change.
     */
    fun savedAs(name: String): EqSettings {
        val cleanName = normalisePresetName(name)
        val curve = gainsDb
        val existing = savedPresets.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        val presets = if (existing == null) {
            savedPresets + SavedPreset(nextSavedId(), cleanName, curve)
        } else {
            savedPresets.map { if (it.id == existing.id) it.copy(gainsDb = curve) else it }
        }
        val id = existing?.id ?: presets.last().id
        return copy(enabled = true, choice = EqChoice.Saved(id), savedPresets = presets)
    }

    /** Renames saved preset [id]; nothing else changes. */
    fun withRenamed(id: Int, name: String): EqSettings {
        val cleanName = normalisePresetName(name)
        return copy(
            savedPresets = savedPresets.map { if (it.id == id) it.copy(name = cleanName) else it }
        )
    }

    /**
     * Deletes saved preset [id]. Deleting the chosen one keeps the sound: its curve becomes the
     * Custom slot, and Custom is chosen (spec "Delete and Undo").
     */
    fun withDeleted(id: Int): EqSettings {
        val remaining = savedPresets.filter { it.id != id }
        if (choice != EqChoice.Saved(id)) return copy(savedPresets = remaining)
        return copy(choice = EqChoice.CUSTOM, customGainsDb = gainsDb, savedPresets = remaining)
    }

    /** The gains for the engine, or null to bypass it: off, or every band at 0 dB. */
    fun activeGainsDb(): List<Float>? =
        gainsDb.takeIf { enabled && it.any { gainDb -> gainDb != 0f } }

    // A deleted preset's id may come back; nothing points at a deleted preset, so that's safe.
    private fun nextSavedId(): Int = (savedPresets.maxOfOrNull { it.id } ?: 0) + 1

    companion object {
        val DEFAULT = EqSettings(
            enabled = false,
            choice = EqChoice.Preset(EqPreset.FLAT),
            customGainsDb = FLAT_GAINS
        )
    }
}

/** The Custom slot as saved in "custom_gains": ten comma-separated decimals, "6.5,-3.0,…". */
fun formatCustomGains(gainsDb: List<Float>): String = gainsDb.joinToString(GAIN_SEPARATOR)

/**
 * Reads a saved "custom_gains" value. Anything but exactly ten finite numbers reads as flat;
 * whole numbers saved before the tenths step still read. Each gain goes through [clampGain].
 */
fun parseCustomGains(value: String?): List<Float> = value?.let(::parseGainsOrNull) ?: FLAT_GAINS

/**
 * Ten comma-separated finite numbers, each through [clampGain], or null. The saved presets'
 * codec in SavedPresets.kt shares it.
 */
internal fun parseGainsOrNull(value: String): List<Float>? {
    val parts = value.split(GAIN_SEPARATOR)
    val gains = parts.mapNotNull { part -> part.trim().toFloatOrNull()?.takeIf { it.isFinite() } }
    if (parts.size != BAND_COUNT || gains.size != BAND_COUNT) return null
    return gains.map(::clampGain)
}
