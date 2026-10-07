package com.jpd.hz.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TagFingerprintTest {

    @Test
    fun isTheSha256OfSortedFieldLines() {
        // printf 'ALBUM=Discovery\nARTIST=Daft Punk; Pharrell Williams\nTITLE=Café' | shasum -a 256
        val fields = mapOf(
            "TITLE" to "Café",
            "ARTIST" to "Daft Punk; Pharrell Williams",
            "ALBUM" to "Discovery"
        )

        assertEquals(
            "947cc1d19a4db848ee66758e4add0a30c7a4cf7e5e1977c310238dcb8d88a967",
            TagFingerprint.of(fields)
        )
    }

    @Test
    fun aSingleFieldHasNoTrailingNewline() {
        // printf 'TITLE=x' | shasum -a 256
        assertEquals(
            "6878ddcc362407b3061f44b89c175135bc93de61eb36736bf6dc73d495690ccc",
            TagFingerprint.of(mapOf("TITLE" to "x"))
        )
    }

    @Test
    fun fieldOrderDoesNotMatter() {
        assertEquals(
            TagFingerprint.of(mapOf("TITLE" to "a", "GENRE" to "b")),
            TagFingerprint.of(mapOf("GENRE" to "b", "TITLE" to "a"))
        )
    }

    @Test
    fun anyChangedValueChangesIt() {
        assertNotEquals(
            TagFingerprint.of(mapOf("GENRE" to "Rock")),
            TagFingerprint.of(mapOf("GENRE" to "Rock; Pop"))
        )
    }
}
