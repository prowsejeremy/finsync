package com.jpd.hz.library

import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.MediaSource
import com.jpd.hz.model.MediaStream
import com.jpd.hz.model.NameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val track = catalogueTracksFrom(listOf(audio("t1", sources = listOf(source)))).single()
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
        val track = catalogueTracksFrom(listOf(audio("t1", sources = listOf(source)))).single()
        assertEquals("mov", track.codec)
        assertEquals(256_000, track.bitrate)
        assertNull(track.bitDepth)
        assertNull(track.sampleRate)
    }

    @Test
    fun `item without media sources uses the item container and leaves the rest null`() {
        val track = catalogueTracksFrom(listOf(audio("t1", container = "MP3"))).single()
        assertEquals("mp3", track.codec)
        assertNull(track.bitrate)
        assertNull(track.size)
    }

    @Test
    fun `duration comes from run time ticks and stays null without them`() {
        val tracks = catalogueTracksFrom(listOf(audio("t1"), audio("t2", ticks = null)))
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
        val track = catalogueTracksFrom(listOf(item)).single()
        assertEquals(listOf("A", "B"), track.artistNames)
        assertEquals(listOf("a", "b"), track.artistIds)
    }

    @Test
    fun `tracks with no album are kept`() {
        val tracks = catalogueTracksFrom(listOf(audio("t1", albumId = null), audio("t2")))
        assertEquals(listOf("t1", "t2"), tracks.map { it.itemId })
        assertNull(tracks.first().albumId)
    }
}
