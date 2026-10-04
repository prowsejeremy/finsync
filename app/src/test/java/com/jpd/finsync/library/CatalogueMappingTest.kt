package com.jpd.finsync.library

import com.jpd.finsync.model.MediaItem
import com.jpd.finsync.model.MediaSource
import com.jpd.finsync.model.MediaStream
import com.jpd.finsync.model.NameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueMappingTest {

    private fun audio(
        id: String,
        albumId: String? = "alb1",
        album: String? = "Album One",
        albumArtist: String? = "Artist A",
        artists: List<String>? = listOf("Artist A"),
        artistItems: List<NameId>? = listOf(NameId(name = "Artist A", id = "art1")),
        year: Int? = 2018,
        container: String? = null,
        sources: List<MediaSource>? = null,
        ticks: Long? = 2_390_000_000L
    ) = MediaItem(
        id = id,
        name = "Track $id",
        type = "Audio",
        albumArtist = albumArtist,
        album = album,
        albumId = albumId,
        trackNumber = 1,
        discNumber = 1,
        runTimeTicks = ticks,
        container = container,
        mediaSources = sources,
        year = year,
        artists = artists,
        artistItems = artistItems
    )

    @Test
    fun `audio stream supplies codec, bit depth, sample rate and bitrate`() {
        val stream = MediaStream(
            type = "Audio", codec = "flac", bitRate = 833_000, sampleRate = 44_100, bitDepth = 16
        )
        val source = MediaSource(
            id = "s1", container = "flac", size = 63_600_000L, bitrate = 900_000,
            mediaStreams = listOf(stream)
        )
        val track = catalogueFrom(listOf(audio("t1", sources = listOf(source)))).tracks.single()
        assertEquals("flac", track.codec)
        assertEquals(16, track.bitDepth)
        assertEquals(44_100, track.sampleRate)
        assertEquals(833_000, track.bitrate)
        assertEquals(63_600_000L, track.size)
    }

    @Test
    fun `codec falls back to the first container and bitrate to the media source`() {
        val source = MediaSource(
            id = "s1", container = "mov,mp4,m4a", bitrate = 256_000, mediaStreams = emptyList()
        )
        val track = catalogueFrom(listOf(audio("t1", sources = listOf(source)))).tracks.single()
        assertEquals("mov", track.codec)
        assertEquals(256_000, track.bitrate)
        assertNull(track.bitDepth)
        assertNull(track.sampleRate)
    }

    @Test
    fun `item without media sources uses the item container and leaves the rest null`() {
        val track = catalogueFrom(listOf(audio("t1", container = "MP3"))).tracks.single()
        assertEquals("mp3", track.codec)
        assertNull(track.bitrate)
        assertNull(track.size)
    }

    @Test
    fun `duration comes from run time ticks and stays null without them`() {
        val tracks = catalogueFrom(listOf(audio("t1"), audio("t2", ticks = null))).tracks
        assertEquals(239_000L, tracks[0].durationMs)
        assertNull(tracks[1].durationMs)
    }

    @Test
    fun `artist names and ids come from the item`() {
        val item = audio(
            "t1",
            artists = listOf("A", "B"),
            artistItems = listOf(NameId(name = "A", id = "a"), NameId(name = "B", id = "b"))
        )
        val track = catalogueFrom(listOf(item)).tracks.single()
        assertEquals(listOf("A", "B"), track.artistNames)
        assertEquals(listOf("a", "b"), track.artistIds)
    }

    @Test
    fun `tracks are grouped into one album per album id`() {
        val items = listOf(
            audio("t1", year = null),
            audio("t2", year = 2018),
            audio("t3", albumId = "alb2", album = null, albumArtist = null, artists = listOf("Solo"))
        )
        val albums = catalogueFrom(items).albums.associateBy { it.albumId }
        assertEquals(2, albums.size)
        assertEquals("Album One", albums.getValue("alb1").name)
        assertEquals("Artist A", albums.getValue("alb1").albumArtist)
        assertEquals(2018, albums.getValue("alb1").year)
        assertEquals("Unknown Album", albums.getValue("alb2").name)
        assertEquals("Solo", albums.getValue("alb2").albumArtist)
    }

    @Test
    fun `tracks with no album are kept without creating an album`() {
        val catalogue = catalogueFrom(listOf(audio("t1", albumId = null)))
        assertTrue(catalogue.albums.isEmpty())
        assertEquals(listOf("t1"), catalogue.tracks.map { it.itemId })
        assertNull(catalogue.tracks.single().albumId)
    }
}
