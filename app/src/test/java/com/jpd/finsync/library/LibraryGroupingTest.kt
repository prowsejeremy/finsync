package com.jpd.finsync.library

import com.jpd.finsync.db.ArtistAlbumRow
import com.jpd.finsync.db.GenreTrackRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryGroupingTest {

    private fun album(id: String, name: String = "Album $id", year: Int? = null, tracks: Int = 1) =
        AlbumSummary(
            albumId = id,
            name = name,
            albumArtist = null,
            year = year,
            downloadedTrackCount = tracks,
            artworkPath = null
        )

    private fun song(
        id: String,
        albumId: String? = "alb1",
        albumName: String? = "Album",
        albumYear: Int? = null,
        disc: Int? = 1,
        number: Int? = null,
        title: String = "Song $id"
    ) = SongRow(
        itemId = id,
        title = title,
        artists = null,
        albumId = albumId,
        albumName = albumName,
        albumYear = albumYear,
        discNumber = disc,
        trackNumber = number,
        durationMs = null,
        artworkPath = null
    )

    @Test
    fun `album artists skip deselected albums`() {
        val credits = listOf(
            ArtistAlbumRow("kurt", "Kurt Vile", "alb1"),
            ArtistAlbumRow("kurt", "Kurt Vile", "alb2"),
            ArtistAlbumRow("kim", "Kim Gordon", "alb3")
        )
        val artists = albumArtistSummaries(credits, setOf("alb1", "alb2")) { null }
        assertEquals(listOf(ArtistSummary("kurt", "Kurt Vile", 2, null)), artists)
    }

    @Test
    fun `album artists are A to Z ignoring case, with their photo`() {
        val credits = listOf(
            ArtistAlbumRow("r", "radiohead", "a1"),
            ArtistAlbumRow("d", "Daft Punk", "a2")
        )
        val artists = albumArtistSummaries(credits, emptySet()) { id -> "/photos/$id.jpg" }
        assertEquals(listOf("Daft Punk", "radiohead"), artists.map { it.name })
        assertEquals("/photos/d.jpg", artists.first().photoPath)
    }

    @Test
    fun `genres count visible albums and songs, keeping tracks without an album`() {
        val tags = listOf(
            GenreTrackRow("rock", "Indie Rock", "t1", "alb1"),
            GenreTrackRow("rock", "Indie Rock", "t2", "alb1"),
            GenreTrackRow("rock", "Indie Rock", "t3", null),
            GenreTrackRow("rock", "Indie Rock", "t4", "alb2"),
            GenreTrackRow("jazz", "Jazz", "t5", "alb2")
        )
        assertEquals(
            listOf(GenreSummary("rock", "Indie Rock", 1, 3)),
            genreSummaries(tags, setOf("alb1"))
        )
    }

    @Test
    fun `albums go newest first, then by name, with undated albums last`() {
        val albums = listOf(
            album("a", "b side", 2015),
            album("b", "Undated"),
            album("c", "Zed", 2020),
            album("d", "A side", 2015)
        )
        assertEquals(
            listOf("c", "d", "a", "b"),
            albums.sortedWith(GROUP_ALBUM_ORDER).map { it.albumId }
        )
    }

    @Test
    fun `group songs follow album order, then disc, number and title`() {
        val songs = listOf(
            song("s1", albumId = "old", albumName = "Old", albumYear = 2015, number = 1),
            song("s2", albumId = "new", albumName = "New", albumYear = 2020, disc = 2, number = 1),
            song("s3", albumId = "new", albumName = "New", albumYear = 2020, disc = 1, number = 2),
            song("s4", albumId = null, albumName = null),
            song("s5", albumId = "new", albumName = "New", albumYear = 2020, title = "b"),
            song("s6", albumId = "new", albumName = "New", albumYear = 2020, title = "A")
        )
        assertEquals(
            listOf("s3", "s6", "s5", "s2", "s1", "s4"),
            songs.sortedWith(GROUP_SONG_ORDER).map { it.itemId }
        )
    }

    @Test
    fun `all songs shows when there is more than one album`() {
        assertTrue(
            showsAllSongs(listOf(album("a"), album("b")), listOf(song("s1", "a"), song("s2", "b")))
        )
    }

    @Test
    fun `all songs hides when the songs are exactly the one album's tracks`() {
        assertFalse(
            showsAllSongs(listOf(album("a", tracks = 2)), listOf(song("s1", "a"), song("s2", "a")))
        )
    }

    @Test
    fun `all songs shows for one album plus a track elsewhere, or only some of its tracks`() {
        val one = listOf(album("a", tracks = 2))
        assertTrue(showsAllSongs(one, listOf(song("s1", "a"), song("s2", "a"), song("s3", "b"))))
        assertTrue(showsAllSongs(one, listOf(song("s1", "a"))))
    }

    @Test
    fun `a group that's gone or has no visible songs has no page`() {
        assertNull(groupDetailOf("kurt", "Kurt Vile", null, emptyList(), emptyList()))
        assertNull(groupDetailOf("kurt", null, null, listOf(album("a")), listOf(song("s1", "a"))))
    }
}
