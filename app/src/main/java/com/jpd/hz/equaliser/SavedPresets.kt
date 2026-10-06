package com.jpd.hz.equaliser

/** The longest name a saved preset can have; the name field stops there. */
const val MAX_PRESET_NAME_LENGTH = 30

// One preset per line, "<id>|<gains>|<name>". The name goes last, so it may hold a bar; names
// are normalised, so none holds a line break.
private const val LINE_SEPARATOR = "\n"
private const val FIELD_SEPARATOR = "|"
private const val FIELD_COUNT = 3
private val WHITESPACE_RUN = Regex("\\s+")

/** A curve the user saved under a name (saved-presets spec "Data"). */
data class SavedPreset(val id: Int, val name: String, val gainsDb: List<Float>) {
    init {
        require(id > 0) { "Ids start at 1" }
        require(name.isNotEmpty() && name == normalisePresetName(name)) { "Unnormalised name" }
        require(gainsDb.size == BAND_COUNT) { "Expected $BAND_COUNT gains" }
    }
}

/** [raw] as it's saved: trimmed, with each run of spaces, tabs or line breaks as one space. */
fun normalisePresetName(raw: String): String = raw.trim().replace(WHITESPACE_RUN, " ")

/** What [checkPresetName] found (spec "Name rules"). */
sealed interface PresetNameCheck {

    /** Whether the name may be used: a new name, or one of the user's to replace. */
    val allowsSaving: Boolean get() = this is Allowed || this is Replaces

    /** Nothing but spaces. */
    data object Empty : PresetNameCheck

    /** A built-in preset's name, as [title] shows it. */
    data class BuiltIn(val title: String) : PresetNameCheck

    /** Another saved preset's name, while renaming. */
    data class Taken(val preset: SavedPreset) : PresetNameCheck

    /** One of the user's names while saving: saving gives [preset] the curve on screen. */
    data class Replaces(val preset: SavedPreset) : PresetNameCheck

    /** A free name, or a renamed preset's own: [name] as it will be saved. */
    data class Allowed(val name: String) : PresetNameCheck
}

/**
 * Checks a typed name (spec "Name rules"). [builtInTitles] are the built-in presets' names as the
 * screen shows them, Custom included. [renamingId] is the preset being renamed, or null while
 * saving. Names compare ignoring case.
 */
fun checkPresetName(
    raw: String,
    builtInTitles: List<String>,
    savedPresets: List<SavedPreset>,
    renamingId: Int?
): PresetNameCheck {
    val name = normalisePresetName(raw)
    if (name.isEmpty()) return PresetNameCheck.Empty
    val builtIn = builtInTitles.firstOrNull { it.equals(name, ignoreCase = true) }
    if (builtIn != null) return PresetNameCheck.BuiltIn(builtIn)
    val match = savedPresets.firstOrNull { it.name.equals(name, ignoreCase = true) }
    return when {
        match == null || match.id == renamingId -> PresetNameCheck.Allowed(name)
        renamingId != null -> PresetNameCheck.Taken(match)
        else -> PresetNameCheck.Replaces(match)
    }
}

/** The saved presets as saved in "saved_presets" (spec "Saved settings"). */
fun formatSavedPresets(presets: List<SavedPreset>): String =
    presets.joinToString(LINE_SEPARATOR) { preset ->
        listOf(preset.id.toString(), formatCustomGains(preset.gainsDb), preset.name)
            .joinToString(FIELD_SEPARATOR)
    }

/**
 * Reads a saved "saved_presets" value. A line is dropped if its id isn't a whole number above 0,
 * its gains aren't ten finite numbers or its name is empty, or if its id or name (ignoring case)
 * came up on an earlier line. Gains are clamped and snapped like the Custom slot's.
 */
fun parseSavedPresets(value: String?): List<SavedPreset> {
    if (value.isNullOrEmpty()) return emptyList()
    val presets = mutableListOf<SavedPreset>()
    for (line in value.split(LINE_SEPARATOR)) {
        val preset = parseSavedPreset(line) ?: continue
        val repeated = presets.any { earlier ->
            earlier.id == preset.id || earlier.name.equals(preset.name, ignoreCase = true)
        }
        if (!repeated) presets += preset
    }
    return presets
}

private fun parseSavedPreset(line: String): SavedPreset? {
    val fields = line.split(FIELD_SEPARATOR, limit = FIELD_COUNT)
    if (fields.size != FIELD_COUNT) return null
    val id = fields[0].toIntOrNull()?.takeIf { it > 0 } ?: return null
    val gains = parseGainsOrNull(fields[1]) ?: return null
    val name = normalisePresetName(fields[2]).takeIf { it.isNotEmpty() } ?: return null
    return SavedPreset(id, name, gains)
}
