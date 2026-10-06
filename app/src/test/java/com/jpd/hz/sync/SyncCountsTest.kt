package com.jpd.hz.sync

import com.jpd.hz.db.CataloguePlaylistItem
import com.jpd.hz.db.TrackAlbumRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCountsTest {

    private val tracks = listOf(
        TrackAlbumRow("t1", "alb1"),
        TrackAlbumRow("t2", "alb1"),
        TrackAlbumRow("t3", "alb2"),
        TrackAlbumRow("t4", null)
    )

    private fun counts(
        albums: Set<String>,
        playlists: Set<String> = emptySet(),
        books: Set<String> = emptySet(),
        entries: List<CataloguePlaylistItem> = emptyList(),
        catalogueBooks: Set<String> = emptySet(),
        synced: Set<String> = emptySet()
    ): SyncCounts = syncCountsOf(
        tracks = tracks,
        playlistItems = entries,
        bookIds = catalogueBooks,
        syncedIds = synced,
        selection = SyncSelection(albums, playlists, books)
    )

    @Test
    fun `every album selected counts every track, a specific selection only its albums'`() {
        assertEquals(4, counts(albums = emptySet()).songsTotal)
        assertEquals(4, counts(albums = setOf("all")).songsTotal)
        assertEquals(2, counts(albums = setOf("alb1")).songsTotal)
    }

    @Test
    fun `a track without an album counts only when every album is selected`() {
        val everything = counts(albums = emptySet(), synced = setOf("t4"))
        val specific = counts(albums = setOf("alb1", "alb2"), synced = setOf("t4"))

        assertEquals(4, everything.songsTotal)
        assertEquals(1, everything.songsSynced)
        assertEquals(3, specific.songsTotal)
        assertEquals(0, specific.songsSynced)
    }

    @Test
    fun `a playlist song also in a selected album counts once`() {
        val result = counts(
            albums = setOf("alb1"),
            playlists = setOf("p1"),
            entries = listOf(
                CataloguePlaylistItem("p1", 0, "t1"),
                CataloguePlaylistItem("p1", 1, "t3"),
                CataloguePlaylistItem("p1", 2, "t3"),
                CataloguePlaylistItem("p2", 0, "t4")
            ),
            synced = setOf("t1", "t3")
        )

        // t1 and t2 from the album, t3 once from p1; p2 isn't selected.
        assertEquals(3, result.songsTotal)
        assertEquals(2, result.songsSynced)
    }

    @Test
    fun `a playlist entry that isn't a catalogue track doesn't count`() {
        val result = counts(
            albums = setOf("alb2"),
            playlists = setOf("p1"),
            entries = listOf(CataloguePlaylistItem("p1", 0, "gone"))
        )

        assertEquals(1, result.songsTotal)
    }

    @Test
    fun `selected books count, and only the synced ones as synced`() {
        val result = counts(
            albums = setOf("alb2"),
            books = setOf("b1", "b2"),
            catalogueBooks = setOf("b1", "b2", "b3"),
            synced = setOf("t3", "b2")
        )

        assertEquals(
            SyncCounts(songsSynced = 1, songsTotal = 1, booksSynced = 1, booksTotal = 2),
            result
        )
        assertEquals(3, result.total)
        assertFalse(result.allSynced)
    }

    @Test
    fun `a selected book missing from the catalogue doesn't count`() {
        val result = counts(
            albums = setOf("alb2"),
            books = setOf("b1", "gone"),
            catalogueBooks = setOf("b1"),
            synced = setOf("t3", "b1")
        )

        assertEquals(1, result.booksTotal)
        assertTrue(result.allSynced)
    }

    @Test
    fun `nothing selected counts nothing`() {
        // An empty album set means every album, so "nothing" is a selection matching no track.
        assertEquals(SyncCounts.NONE, counts(albums = setOf("gone"), synced = setOf("t1")))
    }
}
