package com.jpd.hz.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChooseArtworkTest {

    private val track = "/music/Artist/Album/01 Song.flac"

    @Test
    fun `stored path wins when its file exists`() {
        assertEquals("/art/stored.jpg", chooseArtwork("/art/stored.jpg", track) { true })
    }

    @Test
    fun `falls back to folder jpg beside the track`() {
        val existing = setOf("/music/Artist/Album/folder.jpg")
        assertEquals(
            "/music/Artist/Album/folder.jpg",
            chooseArtwork("/art/missing.jpg", track) { it in existing }
        )
    }

    @Test
    fun `uses folder png when there is no folder jpg`() {
        val existing = setOf("/music/Artist/Album/folder.png")
        assertEquals("/music/Artist/Album/folder.png", chooseArtwork(null, track) { it in existing })
    }

    @Test
    fun `returns null when nothing exists`() {
        assertNull(chooseArtwork(null, track) { false })
        assertNull(chooseArtwork(null, null) { true })
    }
}
