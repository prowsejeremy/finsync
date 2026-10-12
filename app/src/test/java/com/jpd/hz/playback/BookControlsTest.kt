package com.jpd.hz.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookControlsTest {

    @Test
    fun `system previous and next skip back 15 s and on 30 s, and other seeks pass`() {
        assertEquals(-15_000L, systemSkipMs(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertEquals(-15_000L, systemSkipMs(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertEquals(30_000L, systemSkipMs(Player.COMMAND_SEEK_TO_NEXT))
        assertEquals(30_000L, systemSkipMs(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertNull(systemSkipMs(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
    }

    @Test
    fun `a speed becomes BASS_FX's tempo change in percent`() {
        assertEquals(20f, tempoPercentFor(1.2f), 0.001f)
        assertEquals(-20f, tempoPercentFor(0.8f), 0.001f)
        assertEquals(100f, tempoPercentFor(2.0f), 0.001f)
        assertEquals(0f, tempoPercentFor(1.0f), 0.001f)
    }

    @Test
    fun `a saved speed snaps to the nearest step, and nonsense plays at 1x`() {
        assertEquals(1.75f, nearestBookSpeed(1.74f), 0f)
        assertEquals(2.0f, nearestBookSpeed(3.0f), 0f)
        assertEquals(1.0f, nearestBookSpeed(Float.NaN), 0f)
        assertEquals(1.0f, nearestBookSpeed(0f), 0f)
    }
}
