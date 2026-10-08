package com.jpd.hz.sync

import com.jpd.hz.library.playlistRowsFrom
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.NameId
import com.jpd.hz.model.ServerCatalogue
import com.jpd.hz.model.ServerPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T2's extra files in the sync folder: artist photos, playlist files and their covers, and the
 * keep set that protects them from cleanup (spec "Other files it writes").
 */
class SyncFolderFilesTest {

    private val daftPunk = NameId("Daft Punk", "dp")

    private fun audio(
        id: String,
        albumId: String = "alb1",
        albumArtist: String? = "Daft Punk",
        albumArtists: List<NameId>? = listOf(daftPunk)
    ) = MediaItem(
        id = id,
        name = "Track $id",
        type = "Audio",
        albumArtist = albumArtist,
        album = "Discovery",
        albumId = albumId,
        path = "/srv/music/$id.flac",
        runTimeTicks = 2_155_000_000L,
        artists = listOf("Daft Punk"),
        albumArtists = albumArtists
    )

    private fun playlist(id: String, name: String, vararg entryIds: String) = ServerPlaylist(
        MediaItem(id = id, name = name, type = "Playlist"),
        entryIds.map { MediaItem(id = it, name = "Entry $it", type = "Audio") }
    )

    @Test
    fun `the plan carries each selected playlist's tracks in order, repeats included`() {
        val catalogue = ServerCatalogue(
            audio = listOf(audio("t1"), audio("t2"), audio("t3", albumId = "alb2")),
            playlists = emptyList()
        )
        val written = playlistRowsFrom(
            listOf(
                playlist("p1", "Road Trip", "t3", "t1", "gone", "t3"),
                playlist("p2", "Not chosen", "t2")
            )
        )

        val plan = syncPlanOf(catalogue, written, SyncSelection(setOf("alb1"), setOf("p1"), emptySet()))

        val road = plan.playlists.single()
        assertEquals("p1", road.playlistId)
        assertEquals("Road Trip", road.name)
        assertEquals(listOf("t3", "t1", "t3"), road.items.map { it.id })
        assertEquals(setOf("p1"), plan.playlistIds)
    }

    @Test
    fun `each album artist's folder gets one photo, from its first album artist`() {
        val photos = artistPhotosOf(
            listOf(
                audio("t1", albumArtists = listOf(daftPunk, NameId("Romanthony", "ro"))),
                audio("t2", albumId = "alb2", albumArtists = listOf(NameId("Other", "zz"))),
                audio("t3", albumArtist = "Air", albumArtists = listOf(NameId("Air", "air"))),
                audio("t4", albumArtist = null, albumArtists = listOf(NameId("X", "x"))),
                audio("t5", albumArtist = "Nobody", albumArtists = null)
            )
        )

        assertEquals(
            mapOf("Music/Daft Punk/artist.jpg" to "dp", "Music/Air/artist.jpg" to "air"),
            photos
        )
    }

    @Test
    fun `a playlist entry points from Playlists to the track, with its length and label`() {
        val entry = playlistEntryOf(audio("t1"))

        assertEquals("../Music/Daft Punk/Discovery/t1.flac", entry.path)
        assertEquals(215L, entry.seconds)
        assertEquals("Daft Punk - Track t1", entry.label)

        val bare = playlistEntryOf(
            MediaItem(id = "t9", name = "Untitled", type = "Audio", path = "/srv/x/t9.mp3")
        )
        assertEquals("../Music/Unknown Artist/Unknown Album/t9.mp3", bare.path)
        assertEquals(null, bare.seconds)
        assertEquals("Untitled", bare.label)
    }

    @Test
    fun `the keep set holds the artist photos and each playlist's file and cover`() {
        val plan = SyncPlan(
            tracks = listOf(audio("t1")),
            books = emptyList(),
            playlistIds = setOf("p1", "p2"),
            playlists = listOf(
                PlannedPlaylist("p1", "Road Trip", listOf(audio("t1"))),
                PlannedPlaylist("p2", "road trip", emptyList())
            )
        )

        val keep = filesToKeep(File("/sync"), plan)

        assertEquals(
            setOf(
                "/sync/Music/Daft Punk/Discovery/t1.flac",
                "/sync/Music/Daft Punk/Discovery/folder.jpg",
                "/sync/Music/Daft Punk/artist.jpg",
                "/sync/Playlists/Road Trip.m3u8",
                "/sync/Playlists/Road Trip.jpg",
                "/sync/Playlists/road trip (2).m3u8",
                "/sync/Playlists/road trip (2).jpg"
            ),
            keep
        )
        assertTrue(playlistFileNamesOf(plan).values.all { it.isNotBlank() })
    }
}
