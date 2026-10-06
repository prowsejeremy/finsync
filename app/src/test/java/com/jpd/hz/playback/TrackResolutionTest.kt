package com.jpd.hz.playback

import com.jpd.hz.db.CatalogueTrack
import com.jpd.hz.library.PlayableSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackResolutionTest {

    private fun source(artists: List<String> = listOf("Kurt Vile", "Kim Gordon")) = PlayableSource(
        track = CatalogueTrack(
            itemId = "t1",
            albumId = "a1",
            name = "Check Baby",
            artistNames = artists,
            artistIds = emptyList(),
            albumArtist = "Kurt Vile",
            discNumber = 1,
            trackNumber = 4,
            durationMs = 420_000L,
            codec = "flac",
            bitDepth = 16,
            sampleRate = 44_100,
            bitrate = 833_000,
            size = 1L
        ),
        localPath = "/music/Kurt Vile/Bottle It In/04 Check Baby.flac",
        albumName = "Bottle It In",
        albumArtistId = "ar1",
        artworkPath = "/music/Kurt Vile/Bottle It In/folder.jpg"
    )

    @Test
    fun `no catalogue or sync row resolves to nothing`() {
        assertNull(resolveTrack(null) { 1L })
    }

    @Test
    fun `a missing file resolves to nothing`() {
        assertNull(resolveTrack(source()) { null })
    }

    @Test
    fun `maps the catalogue track and takes the size from the file`() {
        val track = resolveTrack(source()) { 63_600_000L }!!
        assertEquals("t1", track.itemId)
        assertEquals("/music/Kurt Vile/Bottle It In/04 Check Baby.flac", track.path)
        assertEquals("Check Baby", track.title)
        assertEquals("Kurt Vile, Kim Gordon", track.artists)
        assertEquals("Bottle It In", track.albumTitle)
        assertEquals("a1", track.albumId)
        assertEquals("Kurt Vile", track.albumArtist)
        assertEquals("ar1", track.albumArtistId)
        assertEquals("/music/Kurt Vile/Bottle It In/folder.jpg", track.artworkPath)
        assertEquals(4, track.trackNumber)
        assertEquals(1, track.discNumber)
        assertEquals(420_000L, track.durationMs)
        assertEquals("flac", track.codec)
        assertEquals(16, track.bitDepth)
        assertEquals(44_100, track.sampleRate)
        assertEquals(833_000, track.bitrate)
        assertEquals(63_600_000L, track.fileSize)
    }

    @Test
    fun `artists fall back to the album artist`() {
        assertEquals("Kurt Vile", resolveTrack(source(artists = emptyList())) { 1L }!!.artists)
    }

    @Test
    fun `start stays on the requested track when it resolved`() {
        assertEquals(1, remapStartIndex(listOf(true, true, true), 1))
    }

    @Test
    fun `start moves to the next resolved track`() {
        assertEquals(1, remapStartIndex(listOf(true, false, true), 1))
    }

    @Test
    fun `start falls back to the previous resolved track`() {
        assertEquals(1, remapStartIndex(listOf(true, true, false), 2))
    }

    @Test
    fun `no resolved track gives no start`() {
        assertNull(remapStartIndex(listOf(false, false), 0))
    }

    @Test
    fun `an unset start index counts as the first track`() {
        assertEquals(0, remapStartIndex(listOf(false, true), -1))
    }
}
