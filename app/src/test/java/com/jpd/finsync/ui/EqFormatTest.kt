package com.jpd.finsync.ui

import com.jpd.finsync.equaliser.BAND_COUNT
import com.jpd.finsync.equaliser.EqPreset
import com.jpd.finsync.equaliser.EqSettings
import com.jpd.finsync.equaliser.MAX_GAIN_DB
import com.jpd.finsync.equaliser.MIN_GAIN_DB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EqFormatTest {

    private val flat = List(BAND_COUNT) { 0f }
    private val bassBoostOn =
        EqSettings(enabled = true, preset = EqPreset.BASS_BOOST, customGainsDb = flat)

    @Test
    fun `boosts get a plus sign, and every gain shows one decimal`() {
        assertEquals("+6.0", signedGainText(6f))
        assertEquals("+6.5", signedGainText(6.5f))
        assertEquals("+12.0", signedGainText(MAX_GAIN_DB))
        assertEquals("0.0", signedGainText(0f))
    }

    @Test
    fun `cuts use a real minus sign, not a hyphen`() {
        assertEquals("−3.0", signedGainText(-3f))
        assertEquals("−0.1", signedGainText(-0.1f))
        assertEquals("−12.0", signedGainText(MIN_GAIN_DB))
    }

    @Test
    fun `a slider's float noise shows as the nearest tenth`() {
        assertEquals("+0.7", signedGainText(0.70000005f))
        assertEquals("−2.9", signedGainText(-2.8999999f))
        assertEquals("0.0", signedGainText(-0.04f))
    }

    @Test
    fun `the Settings summary names the preset only while on`() {
        assertNull(summaryPreset(EqSettings.DEFAULT))
        assertNull(summaryPreset(bassBoostOn.withEnabled(false)))
        assertEquals(EqPreset.BASS_BOOST, summaryPreset(bassBoostOn))
        assertEquals(EqPreset.CUSTOM, summaryPreset(bassBoostOn.withBand(0, 3f)))
    }
}
