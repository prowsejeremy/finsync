package com.jpd.finsync.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BookFormatTest {

    @Test
    fun `under a gigabyte a size reads in whole megabytes`() {
        assertEquals("412 MB", formatFileSize(412_300_000L))
        assertEquals("1 MB", formatFileSize(600_000L))
    }

    @Test
    fun `from a gigabyte a size reads in gigabytes to one decimal`() {
        assertEquals("1.0 GB", formatFileSize(1_000_000_000L))
        assertEquals("1.2 GB", formatFileSize(1_249_000_000L))
    }

    @Test
    fun `speeds read with one decimal, or two when needed`() {
        assertEquals("0.8×", formatSpeed(0.8f))
        assertEquals("1.0×", formatSpeed(1.0f))
        assertEquals("1.75×", formatSpeed(1.75f))
        assertEquals("2.0×", formatSpeed(2.0f))
    }
}
