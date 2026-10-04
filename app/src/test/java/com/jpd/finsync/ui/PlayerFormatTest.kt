package com.jpd.finsync.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerFormatTest {

    @Test
    fun `full info row`() {
        assertEquals(
            "FLAC · 16-bit · 44.1 kHz · 833 kbps · 63.6 MB",
            formatInfoRow(TrackInfo("flac", 16, 44_100, 833_000, 63_600_000L))
        )
    }

    @Test
    fun `codec is upper-cased`() {
        assertEquals("AAC", formatInfoRow(TrackInfo(codec = "aac")))
    }

    @Test
    fun `whole kilohertz has no decimal`() {
        assertEquals("48 kHz", formatInfoRow(TrackInfo(sampleRate = 48_000)))
    }

    @Test
    fun `kilohertz rounds to one decimal`() {
        assertEquals("22.1 kHz", formatInfoRow(TrackInfo(sampleRate = 22_050)))
        assertEquals("88.2 kHz", formatInfoRow(TrackInfo(sampleRate = 88_200)))
    }

    @Test
    fun `bitrate is whole kbps`() {
        assertEquals("320 kbps", formatInfoRow(TrackInfo(bitrate = 320_499)))
        assertEquals("257 kbps", formatInfoRow(TrackInfo(bitrate = 256_600)))
    }

    @Test
    fun `size is megabytes of a million bytes to one decimal`() {
        assertEquals("4.0 MB", formatInfoRow(TrackInfo(sizeBytes = 4_049_999L)))
        assertEquals("4.1 MB", formatInfoRow(TrackInfo(sizeBytes = 4_050_001L)))
    }

    @Test
    fun `missing parts are left out`() {
        assertEquals(
            "MP3 · 44.1 kHz · 320 kbps",
            formatInfoRow(TrackInfo("mp3", null, 44_100, 320_000, null))
        )
    }

    @Test
    fun `nothing known gives an empty row`() {
        assertEquals("", formatInfoRow(TrackInfo()))
    }

    @Test
    fun `progress is in thousandths and clamped`() {
        assertEquals(250, progressPermille(30_000L, 120_000L))
        assertEquals(0, progressPermille(0L, 0L))
        assertEquals(1_000, progressPermille(200L, 100L))
        assertEquals(0, progressPermille(-5L, 100L))
    }
}
