package com.jpd.finsync.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {

    @Test
    fun `every base role and accent has an opaque dark and light value`() {
        val colours = BaseRole.entries.map { it.key to it.colours } +
            Accent.entries.map { "accent_${it.key}" to Palette.accent(it) }

        assertEquals(BaseRole.entries.size + Accent.entries.size, colours.toMap().size)
        colours.forEach { (name, value) ->
            assertTrue("$name dark", isOpaque(value.dark))
            assertTrue("$name light", isOpaque(value.light))
        }
    }

    @Test
    fun `the base roles are the spec's seven, named as colors xml names them`() {
        assertEquals(
            listOf(
                "bg_primary", "surface_1", "surface_2", "text_primary", "muted", "status_good",
                "error"
            ),
            BaseRole.entries.map { it.key }
        )
    }

    // Full alpha and nothing above 32 bits, so Android's toInt() keeps the colour exactly.
    private fun isOpaque(argb: Long): Boolean = argb in OPAQUE_BLACK..OPAQUE_WHITE

    private companion object {
        const val OPAQUE_BLACK = 0xFF000000
        const val OPAQUE_WHITE = 0xFFFFFFFF
    }
}
