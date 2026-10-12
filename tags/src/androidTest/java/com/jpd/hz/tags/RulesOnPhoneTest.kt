package com.jpd.hz.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The format rules again, on the phone. Android's regex engine isn't Java's: it rejects some
 * flags and widens \d. The JVM tests can't show that.
 */
@RunWith(AndroidJUnit4::class)
class RulesOnPhoneTest {

    @Test
    fun normalisingCollapsesNoBreakSpaces() {
        assertEquals("daft punk", Normalising.normalise(" Daft  Punk\t"))
    }

    @Test
    fun anAlbumIdMatchesTheJvms() {
        assertEquals(
            "daft punk\u001Fdiscovery",
            Normalising.albumIdOf(listOf("Daft Punk"), "Discovery")
        )
    }

    @Test
    fun yearsAndNumbersReadOnlyAsciiDigits() {
        assertEquals(2013, TagReading.yearOf("℗ 2013 Columbia"))
        assertNull(TagReading.yearOf("٢٠١٣"))
        assertEquals(3, TagReading.leadingNumber("3/12"))
        assertNull(TagReading.leadingNumber("٣"))
    }

    @Test
    fun valuesSplitAsOnTheJvm() {
        assertEquals(
            listOf("Rock", "Pop"),
            TagReading.splitValues(listOf(" Rock ;; Pop;", "rock"))
        )
    }

    @Test
    fun theFingerprintMatchesTheJvms() {
        assertEquals(
            "6878ddcc362407b3061f44b89c175135bc93de61eb36736bf6dc73d495690ccc",
            TagFingerprint.of(mapOf("TITLE" to "x"))
        )
    }
}
