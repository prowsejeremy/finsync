package com.jpd.hz.library

import com.jpd.hz.db.CatalogueBook
import com.jpd.hz.db.CataloguePlaylist
import com.jpd.hz.db.CataloguePlaylistItem
import com.jpd.hz.library.db.LibraryPlaylist
import com.jpd.hz.library.db.PlaylistEntryRow
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.ServerCatalogue
import com.jpd.hz.model.ServerPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistRulesTest {

    private fun entry(playlistId: String, position: Int, durationMs: Long? = 60_000L) =
        PlaylistEntryRow(
            playlistId = playlistId,
            position = position,
            durationMs = durationMs,
            artworkPath = "/art/$position.jpg",
            embeddedArt = null
        )

    private fun item(id: String, type: String = "Audio") =
        MediaItem(id = id, name = "Item $id", type = type)

    @Test
    fun `only playlists with a song in the library are listed, A to Z`() {
        val playlists = listOf(
            LibraryPlaylist("p1", "zed", null),
            LibraryPlaylist("p2", "Alpha", null),
            LibraryPlaylist("p3", "Beta", null)
        )
        // p3 has no song in the library.
        val summaries = playlistSummaries(playlists, listOf(entry("p1", 0), entry("p2", 0)))
        assertEquals(listOf("p2", "p1"), summaries.map { it.playlistId })
    }

    @Test
    fun `a playlist row counts its songs and falls back to the first one's art`() {
        val entries = listOf(entry("p1", 4, 30_000L), entry("p1", 1, 90_000L), entry("p1", 2, null))
        val summary =
            playlistSummaries(listOf(LibraryPlaylist("p1", "Mix", null)), entries).single()
        assertEquals(3, summary.songCount)
        assertEquals(listOf(90_000L, null, 30_000L), summary.durationsMs)
        assertEquals("/art/1.jpg", summary.coverPath)
    }

    @Test
    fun `a playlist's own cover wins`() {
        val summary = playlistSummaries(
            listOf(LibraryPlaylist("p1", "Mix", "/lib/Playlists/Mix.jpg")),
            listOf(entry("p1", 0))
        ).single()
        assertEquals("/lib/Playlists/Mix.jpg", summary.coverPath)
    }

    @Test
    fun `an empty album selection or one with all selects every album`() {
        assertTrue(selectsEveryAlbum(emptySet()))
        assertTrue(selectsEveryAlbum(setOf("all")))
        assertFalse(selectsEveryAlbum(setOf("alb1")))
        assertTrue(isAlbumSelected("alb1", setOf("alb1")))
        assertFalse(isAlbumSelected("alb3", setOf("alb1")))
    }

    @Test
    fun `playlist rows keep audio entries in order with repeats and drop playlists without audio`() {
        val rows = playlistRowsFrom(
            listOf(
                ServerPlaylist(
                    item("p1", "Playlist"),
                    listOf(item("t3"), item("v1", "Video"), item("t1"), item("t3"))
                ),
                ServerPlaylist(item("p2", "Playlist"), listOf(item("v2", "Video")))
            )
        )
        assertEquals(listOf(CataloguePlaylist("p1", "Item p1")), rows.playlists)
        assertEquals(
            listOf(
                CataloguePlaylistItem("p1", 0, "t3"),
                CataloguePlaylistItem("p1", 1, "t1"),
                CataloguePlaylistItem("p1", 2, "t3")
            ),
            rows.playlistItems
        )
    }

    @Test
    fun `a failed fetch keeps that part's previous rows`() {
        val previous = PlaylistBookRows(
            playlists = listOf(
                CataloguePlaylist("p1", "Old one"),
                CataloguePlaylist("p2", "Old two")
            ),
            playlistItems = listOf(
                CataloguePlaylistItem("p1", 0, "t1"),
                CataloguePlaylistItem("p2", 0, "t2")
            ),
            books = listOf(CatalogueBook("b1", "Dune", null, null, null, null, null, null, null))
        )
        val fresh = PlaylistBookRows(
            playlists = listOf(CataloguePlaylist("p1", "New one")),
            playlistItems = listOf(CataloguePlaylistItem("p1", 0, "t9"))
        )
        // p2's entries and the book list didn't load.
        val failed = ServerCatalogue(
            audio = emptyList(),
            playlists = emptyList(),
            failedPlaylistIds = setOf("p2"),
            booksFailed = true
        )
        val kept = keepFailedParts(fresh, previous, failed)
        assertEquals(listOf("New one", "Old two"), kept.playlists.map { it.name })
        assertEquals(listOf("t9", "t2"), kept.playlistItems.map { it.itemId })
        assertEquals(previous.books, kept.books)

        // When the playlist list itself didn't load, every playlist keeps its rows.
        val listFailed =
            ServerCatalogue(audio = emptyList(), playlists = emptyList(), playlistsFailed = true)
        val nothingFresh = PlaylistBookRows(playlists = emptyList(), playlistItems = emptyList())
        assertEquals(
            previous.playlistItems,
            keepFailedParts(nothingFresh, previous, listFailed).playlistItems
        )
    }
}
