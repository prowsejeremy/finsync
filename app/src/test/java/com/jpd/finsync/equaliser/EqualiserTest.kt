package com.jpd.finsync.equaliser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualiserTest {

    private val flat = List(BAND_COUNT) { 0f }
    private val custom = listOf(1f, 2.5f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)

    @Test
    fun `ten octave bands with 1 kHz at index 5`() {
        assertEquals(
            listOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f),
            BAND_CENTRES_HZ
        )
    }

    @Test
    fun `band labels drop the fraction and switch to kHz from 1 kHz`() {
        assertEquals(
            listOf(31, 62, 125, 250, 500, 1, 2, 4, 8, 16),
            List(BAND_COUNT) { bandLabel(it).value }
        )
        assertEquals(List(BAND_COUNT) { it >= 5 }, List(BAND_COUNT) { bandLabel(it).kilohertz })
    }

    @Test
    fun `every preset but Custom has ten gains in range, and keys are unique`() {
        EqPreset.entries.filter { it != EqPreset.CUSTOM }.forEach { preset ->
            val gains = checkNotNull(preset.gainsDb)
            assertEquals(preset.key, BAND_COUNT, gains.size)
            assertTrue(preset.key, gains.all { it in MIN_GAIN_DB..MAX_GAIN_DB })
        }
        assertNull(EqPreset.CUSTOM.gainsDb)
        assertEquals(EqPreset.entries.size, EqPreset.entries.map { it.key }.toSet().size)
        assertEquals(flat, EqPreset.FLAT.gainsDb)
    }

    @Test
    fun `fromKey reads keys, and null or unknown keys as Flat`() {
        assertEquals(EqPreset.BASS_BOOST, EqPreset.fromKey("bass_boost"))
        assertEquals(EqPreset.CUSTOM, EqPreset.fromKey("custom"))
        assertEquals(EqPreset.FLAT, EqPreset.fromKey(null))
        assertEquals(EqPreset.FLAT, EqPreset.fromKey("loudness"))
    }

    @Test
    fun `the default is off and Flat with a flat Custom slot`() {
        assertFalse(EqSettings.DEFAULT.enabled)
        assertEquals(EqPreset.FLAT, EqSettings.DEFAULT.preset)
        assertEquals(flat, EqSettings.DEFAULT.customGainsDb)
    }

    @Test
    fun `moving a band turns the EQ on and makes the shown curve the Custom slot`() {
        val bassBoost =
            EqSettings(enabled = false, preset = EqPreset.BASS_BOOST, customGainsDb = custom)

        val moved = bassBoost.withBand(3, -4.5f)

        assertTrue(moved.enabled)
        assertEquals(EqPreset.CUSTOM, moved.preset)
        assertEquals(listOf(6f, 5f, 4f, -4.5f, 0f, 0f, 0f, 0f, 0f, 0f), moved.customGainsDb)
        assertEquals(moved.customGainsDb, moved.gainsDb)
    }

    @Test
    fun `a band's gain is clamped to the slider's range`() {
        assertEquals(MAX_GAIN_DB, EqSettings.DEFAULT.withBand(0, 20f).gainsDb[0])
        assertEquals(MIN_GAIN_DB, EqSettings.DEFAULT.withBand(9, -20f).gainsDb[9])
    }

    @Test
    fun `a band's gain snaps to the nearest tenth of a decibel`() {
        assertEquals(3f, EqSettings.DEFAULT.withBand(2, 3.04f).gainsDb[2])
        assertEquals(3.1f, EqSettings.DEFAULT.withBand(2, 3.06f).gainsDb[2])
        // A slider's float arithmetic lands near the step, not on it.
        assertEquals(0.7f, EqSettings.DEFAULT.withBand(2, 0.70000005f).gainsDb[2])
        assertEquals(-2.9f, EqSettings.DEFAULT.withBand(2, -2.8999999f).gainsDb[2])
        // Not -0.0, which a boxed Float doesn't count as equal to 0.0.
        assertEquals(0f, EqSettings.DEFAULT.withBand(2, -0.04f).gainsDb[2])
    }

    @Test
    fun `choosing a preset keeps the Custom slot, and Custom brings it back`() {
        val customOff =
            EqSettings(enabled = false, preset = EqPreset.CUSTOM, customGainsDb = custom)

        val rock = customOff.withPreset(EqPreset.ROCK)

        assertTrue(rock.enabled)
        assertEquals(EqPreset.ROCK.gainsDb, rock.gainsDb)
        assertEquals(custom, rock.customGainsDb)
        assertEquals(custom, rock.withPreset(EqPreset.CUSTOM).gainsDb)
    }

    @Test
    fun `active gains are null when off or when every band is at 0 dB`() {
        val rock = EqSettings(enabled = true, preset = EqPreset.ROCK, customGainsDb = flat)

        assertEquals(EqPreset.ROCK.gainsDb, rock.activeGainsDb())
        assertNull(rock.withEnabled(false).activeGainsDb())
        assertNull(rock.withPreset(EqPreset.FLAT).activeGainsDb())
        // The Custom slot here is flat too.
        assertNull(rock.withPreset(EqPreset.CUSTOM).activeGainsDb())
    }

    @Test
    fun `custom gains round trip through their saved text`() {
        val gains = listOf(6f, -5.5f, 4f, 0f, 0f, 0f, 0f, 0f, 0.1f, -12f)

        val saved = formatCustomGains(gains)

        assertEquals("6.0,-5.5,4.0,0.0,0.0,0.0,0.0,0.0,0.1,-12.0", saved)
        assertEquals(gains, parseCustomGains(saved))
    }

    @Test
    fun `whole numbers saved before the tenths step still read`() {
        assertEquals(
            listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, -3f),
            parseCustomGains("6,5,4,2,0,0,0,0,0,-3")
        )
    }

    @Test
    fun `saved text that isn't ten numbers reads as flat`() {
        assertEquals(flat, parseCustomGains(null))
        assertEquals(flat, parseCustomGains(""))
        assertEquals(flat, parseCustomGains("1,2,3"))
        assertEquals(flat, parseCustomGains("1,2,3,4,5,6,7,8,9,x"))
        assertEquals(flat, parseCustomGains("NaN,0,0,0,0,0,0,0,0,0"))
        assertEquals(flat, parseCustomGains("1,2,3,4,5,6,7,8,9,10,11"))
    }

    @Test
    fun `saved gains outside the range are clamped, and others snap to a tenth`() {
        assertEquals(
            listOf(12f, -12f, 3f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            parseCustomGains("40,-40,3.04,0,0,0,0,0,0,0")
        )
    }
}
