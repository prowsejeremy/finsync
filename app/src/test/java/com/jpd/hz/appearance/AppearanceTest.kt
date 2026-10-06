package com.jpd.hz.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceTest {

    @Test
    fun `ThemeMode reads each saved key`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromKey("light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("system"))
    }

    @Test
    fun `ThemeMode is dark when nothing or an unknown value is saved`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromKey("sepia"))
    }

    @Test
    fun `Accent reads each saved key`() {
        assertEquals(Accent.GREEN, Accent.fromKey("green"))
        assertEquals(Accent.BLUE, Accent.fromKey("blue"))
        assertEquals(Accent.PURPLE, Accent.fromKey("purple"))
        assertEquals(Accent.PINK, Accent.fromKey("pink"))
        assertEquals(Accent.RED, Accent.fromKey("red"))
    }

    @Test
    fun `Accent is green when nothing or an unknown value is saved`() {
        assertEquals(Accent.GREEN, Accent.fromKey(null))
        assertEquals(Accent.GREEN, Accent.fromKey("teal"))
    }

    @Test
    fun `icons on purple are white in both modes`() {
        val purple = Palette.accent(Accent.PURPLE)

        assertEquals(Palette.ICON_WHITE, onAccent(purple.dark))
        assertEquals(Palette.ICON_WHITE, onAccent(purple.light))
    }

    @Test
    fun `icons on green, blue, pink and red are black in both modes`() {
        listOf(Accent.GREEN, Accent.BLUE, Accent.PINK, Accent.RED).forEach { accent ->
            val colours = Palette.accent(accent)

            assertEquals("$accent dark", Palette.ICON_BLACK, onAccent(colours.dark))
            assertEquals("$accent light", Palette.ICON_BLACK, onAccent(colours.light))
        }
    }

    @Test
    fun `contrast follows WCAG, from 1 for one colour to 21 for black and white`() {
        assertEquals(21.0, contrastRatio(0xFF000000, 0xFFFFFFFF), DELTA)
        assertEquals(21.0, contrastRatio(0xFFFFFFFF, 0xFF000000), DELTA)
        assertEquals(1.0, contrastRatio(0xFF506575, 0xFF506575), DELTA)
    }

    private companion object {
        const val DELTA = 0.0001
    }
}
