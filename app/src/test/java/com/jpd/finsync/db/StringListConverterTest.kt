package com.jpd.finsync.db

import org.junit.Assert.assertEquals
import org.junit.Test

class StringListConverterTest {

    private val converter = StringListConverter()

    @Test
    fun `list round-trips through json`() {
        val names = listOf("Kurt Vile", "Kim Gordon, Jr.")
        assertEquals(names, converter.toList(converter.fromList(names)))
    }

    @Test
    fun `empty list round-trips`() {
        assertEquals(emptyList<String>(), converter.toList(converter.fromList(emptyList())))
    }
}
