package com.jpd.finsync.appearance

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Keeps both colors.xml files equal to Palette.kt (spec "Drift test"). Unit tests run with the
 * app module as their working directory, so the paths are relative to app/.
 */
class PaletteDriftTest {

    private val light = colours("src/main/res/values/colors.xml")
    private val dark = colours("src/main/res/values-night/colors.xml")

    @Test
    fun `values colors xml has the palette's light values`() {
        assertPalette(light) { it.light }
    }

    @Test
    fun `values-night colors xml has the palette's dark values`() {
        assertPalette(dark) { it.dark }
    }

    @Test
    fun `each on_accent colour is the icon rule applied to its accent, in both modes`() {
        listOf("light" to light, "dark" to dark).forEach { (mode, file) ->
            Accent.entries.forEach { accent ->
                val name = "accent_${accent.key}"
                assertEquals("on_$name ($mode)", onAccent(file.getValue(name)), file["on_$name"])
            }
        }
    }

    private fun assertPalette(file: Map<String, Long>, value: (ModeColours) -> Long) {
        BaseRole.entries.forEach { role ->
            assertEquals(role.key, value(role.colours), file[role.key])
        }
        Accent.entries.forEach { accent ->
            val name = "accent_${accent.key}"
            assertEquals(name, value(Palette.accent(accent)), file[name])
        }
    }

    // Each <color name="…">#RRGGBB</color> (or #AARRGGBB) in the file, as an ARGB value.
    private fun colours(path: String): Map<String, Long> {
        val file = File(path)
        assertTrue("${file.absolutePath} not found", file.isFile)
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("color")
        return (0 until nodes.length).associate { index ->
            val element = nodes.item(index) as Element
            element.getAttribute("name") to argbOf(element.textContent.trim())
        }
    }

    private fun argbOf(hex: String): Long {
        val digits = hex.removePrefix("#")
        val value = digits.toLong(HEX_RADIX)
        return if (digits.length == RGB_DIGITS) OPAQUE or value else value
    }

    private companion object {
        const val HEX_RADIX = 16
        const val RGB_DIGITS = 6
        const val OPAQUE = 0xFF000000
    }
}
