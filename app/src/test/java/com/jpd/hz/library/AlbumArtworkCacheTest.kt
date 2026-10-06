package com.jpd.hz.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumArtworkCacheTest {

    @Test
    fun `an album's artwork is chosen once, even when it has none`() {
        var checks = 0
        val cache = AlbumArtworkCache { checks++; false }
        assertNull(cache.artworkFor("alb1", null, "/music/A/One/01.flac"))
        assertNull(cache.artworkFor("alb1", null, "/music/A/One/02.flac"))
        assertEquals(2, checks)
    }

    @Test
    fun `tracks without an album are checked one by one`() {
        val cache = AlbumArtworkCache { it == "/music/A/One/folder.jpg" }
        assertEquals("/music/A/One/folder.jpg", cache.artworkFor(null, null, "/music/A/One/01.flac"))
        assertNull(cache.artworkFor(null, null, "/music/B/Two/01.flac"))
    }
}
