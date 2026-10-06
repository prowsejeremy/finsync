package com.jpd.hz.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeStateTest {

    private val state = ResumeState(
        itemIds = listOf("a", "b", "c"),
        index = 1,
        positionMs = 42_000L,
        repeatMode = 2,
        shuffle = true
    )

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
    fun `an index outside the queue decodes to null`() {
        assertNull(decodeResumeState("""{"itemIds":["a"],"index":3}"""))
    }

    @Test
    fun `an empty queue decodes to null`() {
        assertNull(decodeResumeState("""{"itemIds":[],"index":0}"""))
    }

    @Test
    fun `missing optional fields get defaults`() {
        assertEquals(
            ResumeState(listOf("a"), 0, 0L, 0, false),
            decodeResumeState("""{"itemIds":["a"],"index":0}""")
        )
    }

    @Test
    fun `keepOnly keeps the current track and its position`() {
        assertEquals(
            ResumeState(listOf("b", "c"), 0, 42_000L, 2, true),
            state.keepOnly(setOf("b", "c"))
        )
    }

    @Test
    fun `keepOnly moves to the next available track from the start`() {
        assertEquals(
            ResumeState(listOf("a", "c"), 1, 0L, 2, true),
            state.keepOnly(setOf("a", "c"))
        )
    }

    @Test
    fun `keepOnly with nothing available gives null`() {
        assertNull(state.keepOnly(emptySet()))
    }
}
