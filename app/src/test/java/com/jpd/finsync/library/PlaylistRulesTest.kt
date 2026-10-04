package com.jpd.finsync.library

import com.jpd.finsync.db.CatalogueBook
import com.jpd.finsync.db.CatalogueBookChapter
import com.jpd.finsync.db.CataloguePlaylist
import com.jpd.finsync.db.CataloguePlaylistItem
import com.jpd.finsync.db.PlaylistEntryRow
import com.jpd.finsync.model.MediaItem
import com.jpd.finsync.model.ServerCatalogue
import com.jpd.finsync.model.ServerPlaylist
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
            albumId = "alb$position",
            localPath = "/music/$playlistId-$position.flac",
            storedArtworkPath = null
        )

    private fun item(id: String, type: String = "Audio") =
        MediaItem(id = id, name = "Item $id", type = type)

    @Test
    fun `only selected playlists with a downloaded song are listed, A to Z`() {
        val playlists = listOf(
            CataloguePlaylist("p1", "zed"),
            CataloguePlaylist("p2", "Alpha"),
            CataloguePlaylist("p3", "Beta"),
            CataloguePlaylist("p4", "Gamma")
        )
        // p3 has nothing downloaded; p4 isn't selected.
        val entries = listOf(entry("p1", 0), entry("p2", 0), entry("p4", 0))
        val summaries = playlistSummaries(playlists, entries, setOf("p1", "p2", "p3")) { _, _ ->
            null
        }
        assertEquals(listOf("p2", "p1"), summaries.map { it.playlistId })
    }

    @Test
    fun `a playlist row counts its downloaded songs and falls back to the first one's art`() {
        val entries = listOf(entry("p1", 4, 30_000L), entry("p1", 1, 90_000L), entry("p1", 2, null))
        val summary = playlistSummaries(
            listOf(CataloguePlaylist("p1", "Mix")),
            entries,
            setOf("p1")
        ) { _, first -> "/art/${first.position}.jpg" }.single()
        assertEquals(3, summary.songCount)
        assertEquals(listOf(90_000L, null, 30_000L), summary.durationsMs)
        assertEquals("/art/1.jpg", summary.coverPath)
    }

    @Test
    fun `a specific album selection gains the albums of selected playlists`() {
        val visible = visibleAlbumSelection(setOf("alb1"), setOf("alb2"))
        assertEquals(setOf("alb1", "alb2"), visible)
        assertTrue(isAlbumSelected("alb2", visible))
        assertFalse(isAlbumSelected("alb3", visible))
    }

    @Test
    fun `a selection of everything stays everything`() {
        assertEquals(emptySet<String>(), visibleAlbumSelection(emptySet(), setOf("alb2")))
        assertEquals(setOf("all"), visibleAlbumSelection(setOf("all"), setOf("alb2")))
        assertTrue(selectsEveryAlbum(emptySet()))
        assertFalse(selectsEveryAlbum(setOf("alb1")))
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
            books = listOf(CatalogueBook("b1", "Dune", null, null, null, null, null, null, null)),
            chapters = listOf(CatalogueBookChapter("b1", 0, "One", 0L))
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
        assertEquals(previous.chapters, kept.chapters)

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
