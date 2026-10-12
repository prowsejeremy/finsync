package com.jpd.hz.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeStateTest {

    // "b" was removed from the queue; "a" is playing.
    private val state = ResumeState(
        sourceIds = listOf("a", "b", "c", "d"),
        queue = listOf(2, 0, 3),
        index = 1,
        positionMs = 42_000L,
        repeatMode = 2,
        shuffle = true
    )

    // "a" is listed twice, as a playlist can; the second copy plays first.
    private val twice = ResumeState(listOf("a", "b", "a"), listOf(2, 0, 1), 1, 5_000L, 0, false)

    @Test
    fun `state round-trips through json`() {
        assertEquals(state, decodeResumeState(encodeResumeState(state)))
    }

    @Test
    fun `nothing saved decodes to null`() {
        assertNull(decodeResumeState(null))
        assertNull(decodeResumeState(""))
    }

    @Test
    fun `malformed json decodes to null`() {
        assertNull(decodeResumeState("{not json"))
    }

    @Test
    fun `a save from before queue editing decodes to null`() {
        assertNull(decodeResumeState("""{"itemIds":["a"],"index":0}"""))
    }

    @Test
    fun `an empty or missing source decodes to null`() {
        assertNull(decodeResumeState("""{"sourceIds":[],"queue":[0],"index":0}"""))
        assertNull(decodeResumeState("""{"queue":[0],"index":0}"""))
    }

    @Test
    fun `an empty or missing queue decodes to null`() {
        assertNull(decodeResumeState("""{"sourceIds":["a"],"queue":[],"index":0}"""))
        assertNull(decodeResumeState("""{"sourceIds":["a"],"index":0}"""))
    }

    @Test
    fun `a queue position outside the source decodes to null`() {
        assertNull(decodeResumeState("""{"sourceIds":["a"],"queue":[1],"index":0}"""))
    }

    @Test
    fun `a source position queued twice decodes to null`() {
        assertNull(decodeResumeState("""{"sourceIds":["a","b"],"queue":[0,0],"index":0}"""))
    }

    @Test
    fun `an index outside the queue decodes to null`() {
        assertNull(decodeResumeState("""{"sourceIds":["a","b"],"queue":[1],"index":1}"""))
        assertNull(decodeResumeState("""{"sourceIds":["a","b"],"queue":[1],"index":-1}"""))
    }

    @Test
    fun `a null in the queue decodes to null`() {
        assertNull(decodeResumeState("""{"sourceIds":["a","b"],"queue":[0,null],"index":0}"""))
    }

    @Test
    fun `a source that lists a track twice round-trips`() {
        assertEquals(twice, decodeResumeState(encodeResumeState(twice)))
    }

    @Test
    fun `missing optional fields get defaults`() {
        assertEquals(
            ResumeState(listOf("a", "b"), listOf(1, 0), 0, 0L, 0, false),
            decodeResumeState("""{"sourceIds":["a","b"],"queue":[1,0],"index":0}""")
        )
    }

    @Test
    fun `keepOnly keeps the playing track and renumbers the queue`() {
        assertEquals(
            ResumeState(listOf("a", "c", "d"), listOf(1, 0, 2), 1, 42_000L, 2, true),
            state.keepOnly(listOf(true, false, true, true))
        )
    }

    @Test
    fun `keepOnly drops a missing queued track`() {
        assertEquals(
            ResumeState(listOf("a", "b", "d"), listOf(0, 2), 0, 42_000L, 2, true),
            state.keepOnly(listOf(true, true, false, true))
        )
    }

    @Test
    fun `keepOnly moves to the next track in the queue from the start`() {
        assertEquals(
            ResumeState(listOf("b", "c", "d"), listOf(1, 2), 1, 0L, 2, true),
            state.keepOnly(listOf(false, true, true, true))
        )
    }

    @Test
    fun `keepOnly with nothing in the queue available gives null`() {
        assertNull(state.keepOnly(listOf(false, true, false, false)))
        assertNull(state.keepOnly(listOf(false, false, false, false)))
    }

    @Test
    fun `keepOnly judges a track listed twice at each place`() {
        assertEquals(
            ResumeState(listOf("a", "b"), listOf(0, 1), 0, 5_000L, 0, false),
            twice.keepOnly(listOf(true, true, false))
        )
    }
}
