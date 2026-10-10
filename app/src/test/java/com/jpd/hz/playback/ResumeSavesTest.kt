package com.jpd.hz.playback

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeSavesTest {

    @Test
    fun `a new chapter saves, so the book page's green chapter follows the player`() {
        // BassPlayer reports a new chapter as the current item's title, and nothing else changes.
        assertTrue(savesResume(only(Player.EVENT_MEDIA_METADATA_CHANGED), Player.STATE_READY))
    }

    @Test
    fun `play and pause, seeks, track and queue changes, repeat and shuffle save`() {
        listOf(
            Player.EVENT_PLAY_WHEN_READY_CHANGED,
            Player.EVENT_MEDIA_ITEM_TRANSITION,
            Player.EVENT_POSITION_DISCONTINUITY,
            Player.EVENT_TIMELINE_CHANGED,
            Player.EVENT_REPEAT_MODE_CHANGED,
            Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED
        ).forEach { event ->
            assertTrue("event $event", savesResume(only(event), Player.STATE_READY))
        }
    }

    @Test
    fun `reaching the end saves, and other playback states don't`() {
        val stateChanged = only(Player.EVENT_PLAYBACK_STATE_CHANGED)
        assertTrue(savesResume(stateChanged, Player.STATE_ENDED))
        assertFalse(savesResume(stateChanged, Player.STATE_IDLE))
        assertFalse(savesResume(stateChanged, Player.STATE_READY))
    }

    @Test
    fun `a speed change alone doesn't save`() {
        assertFalse(
            savesResume(only(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED), Player.STATE_READY)
        )
    }

    private fun only(vararg events: Int): (Int) -> Boolean = { it in events }
}
