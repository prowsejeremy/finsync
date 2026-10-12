package com.jpd.hz.equaliser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPresetsTest {

    private val flat = List(BAND_COUNT) { 0f }
    private val nightDrive = SavedPreset(
        id = 1,
        name = "Night drive",
        gainsDb = listOf(3f, 3f, 2f, 1f, 0f, 0f, 1f, 2f, 2f, 1f)
    )
    private val podcasts = SavedPreset(id = 2, name = "Podcasts", gainsDb = flat)
    private val saved = listOf(nightDrive, podcasts)
    // As the screen shows them; the app's list comes from its strings.
    private val builtIns = listOf("Flat", "Bass boost", "Rock", "Custom")

    @Test
    fun `names are trimmed, and each run of spaces, tabs or line breaks becomes one space`() {
        assertEquals("Night drive", normalisePresetName("  Night \t\n drive "))
        assertEquals("", normalisePresetName(" \n "))
    }

    @Test
    fun `a name of only spaces can't be used`() {
        val check = checkPresetName("   ", builtIns, saved, renamingId = null)

        assertEquals(PresetNameCheck.Empty, check)
        assertFalse(check.allowsSaving)
    }

    @Test
    fun `a built-in preset's name can't be used, in any case`() {
        val check = checkPresetName("rock", builtIns, saved, renamingId = null)

        assertEquals(PresetNameCheck.BuiltIn("Rock"), check)
        assertFalse(check.allowsSaving)
        assertEquals(
            PresetNameCheck.BuiltIn("Custom"),
            checkPresetName(" CUSTOM ", builtIns, saved, renamingId = 1)
        )
    }

    @Test
    fun `saving under one of the user's names, in any case, replaces that preset`() {
        val check = checkPresetName(" night  DRIVE", builtIns, saved, renamingId = null)

        assertEquals(PresetNameCheck.Replaces(nightDrive), check)
        assertTrue(check.allowsSaving)
    }

    @Test
    fun `renaming to another preset's name is refused`() {
        val check = checkPresetName("podcasts", builtIns, saved, renamingId = 1)

        assertEquals(PresetNameCheck.Taken(podcasts), check)
        assertFalse(check.allowsSaving)
    }

    @Test
    fun `renaming to the preset's own name in another case is allowed`() {
        val check = checkPresetName("NIGHT drive", builtIns, saved, renamingId = 1)

        assertEquals(PresetNameCheck.Allowed("NIGHT drive"), check)
        assertTrue(check.allowsSaving)
    }

    @Test
    fun `a new name is allowed, as it will be saved`() {
        val check = checkPresetName("  Late   night ", builtIns, saved, renamingId = null)

        assertEquals(PresetNameCheck.Allowed("Late night"), check)
        assertTrue(check.allowsSaving)
    }

    @Test
    fun `saved presets round trip through their saved text, with a bar in a name`() {
        val live = SavedPreset(
            id = 7,
            name = "Rock | live",
            gainsDb = listOf(-0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 12f)
        )

        val text = formatSavedPresets(listOf(nightDrive, live))

        assertEquals(
            "1|3.0,3.0,2.0,1.0,0.0,0.0,1.0,2.0,2.0,1.0|Night drive\n" +
                "7|-0.5,0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0,12.0|Rock | live",
            text
        )
        assertEquals(listOf(nightDrive, live), parseSavedPresets(text))
    }

    @Test
    fun `missing or empty text reads as no presets`() {
        assertEquals(emptyList<SavedPreset>(), parseSavedPresets(null))
        assertEquals(emptyList<SavedPreset>(), parseSavedPresets(""))
    }

    @Test
    fun `broken and repeated lines are dropped, and the rest still read`() {
        val zeros = "0,0,0,0,0,0,0,0,0,0"
        val text = listOf(
            "1|3,3,2,1,0,0,1,2,2,1|Night drive",
            "0|$zeros|Zero id",
            "x|$zeros|Not a number",
            "2|1,2,3|Three gains",
            "3|$zeros|   ",
            "4|$zeros",
            "1|$zeros|Repeated id",
            "5|$zeros|NIGHT DRIVE",
            "6|$zeros|  Podcasts "
        ).joinToString("\n")

        assertEquals(
            listOf(nightDrive, SavedPreset(id = 6, name = "Podcasts", gainsDb = flat)),
            parseSavedPresets(text)
        )
    }

    @Test
    fun `saved gains are clamped and snapped like the Custom slot's`() {
        val preset = parseSavedPresets("1|40,-40,3.04,0,0,0,0,0,0,0|Loud").single()

        assertEquals(listOf(12f, -12f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), preset.gainsDb)
    }
}
