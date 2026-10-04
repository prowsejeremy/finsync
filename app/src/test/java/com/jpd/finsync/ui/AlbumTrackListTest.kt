package com.jpd.finsync.ui

import com.jpd.finsync.db.CatalogueTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTrackListTest {

    private fun track(id: String, disc: Int?, number: Int?) = CatalogueTrack(
        itemId = id,
        albumId = "album",
        name = "Song $id",
        artistNames = listOf("Kurt Vile"),
        artistIds = emptyList(),
        albumArtist = "Kurt Vile",
        discNumber = disc,
        trackNumber = number,
        durationMs = 1_000L,
        codec = null,
        bitDepth = null,
        sampleRate = null,
        bitrate = null,
        size = null
    )

    private fun labels(rows: List<AlbumListRow>) = rows.map { row ->
        when (row) {
            is AlbumListRow.DiscHeading -> "D${row.discNumber}"
            is AlbumListRow.Track -> row.itemId
        }
    }

    @Test
    fun `single disc album has no headings`() {
        val rows = albumListRows(listOf(track("1", 1, 1), track("2", 1, 2)), "Kurt Vile")
        assertTrue(rows.all { it is AlbumListRow.Track })
    }

    @Test
    fun `multi disc album gets a heading before each disc`() {
        val rows = albumListRows(
            listOf(track("1", 1, 1), track("2", 2, 1), track("3", 2, 2)), "Kurt Vile"
        )
        assertEquals(listOf("D1", "1", "D2", "2", "3"), labels(rows))
    }

    @Test
    fun `missing disc numbers count as disc 1`() {
        val rows = albumListRows(listOf(track("1", null, 1), track("2", 1, 2)), "Kurt Vile")
        assertEquals(listOf("1", "2"), labels(rows))
    }

    @Test
    fun `queue index follows the downloaded track order`() {
        val rows = albumListRows(
            listOf(track("1", 1, 1), track("2", 2, 1), track("3", 2, 2)), "Kurt Vile"
        )
        val indexes = rows.filterIsInstance<AlbumListRow.Track>().map { it.queueIndex }
        assertEquals(listOf(0, 1, 2), indexes)
    }

    @Test
    fun `track artists are hidden when they are just the album artist`() {
        assertNull(trackArtists(listOf("kurt vile"), "Kurt Vile"))
        assertNull(trackArtists(emptyList(), "Kurt Vile"))
    }

    @Test
    fun `track artists are shown when they differ from the album artist`() {
        assertEquals(
            "Kurt Vile, Kim Gordon",
            trackArtists(listOf("Kurt Vile", "Kim Gordon"), "Kurt Vile")
        )
        assertEquals("Kim Gordon", trackArtists(listOf("Kim Gordon"), "Kurt Vile"))
        assertEquals("Kim Gordon", trackArtists(listOf("Kim Gordon"), null))
    }
}
