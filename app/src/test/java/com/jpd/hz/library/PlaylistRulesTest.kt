package com.jpd.hz.library

import com.jpd.hz.library.db.LibraryPlaylist
import com.jpd.hz.library.db.PlaylistEntryRow
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The player's Playlists list. The catalogue's playlist rows and the failed-part rule moved to
 * Jellyfin's mapping and the harness with their code (adapter harness spec, "Where the code
 * goes").
 */
class PlaylistRulesTest {

    private fun entry(playlistId: String, position: Int, durationMs: Long? = 60_000L) =
        PlaylistEntryRow(
            playlistId = playlistId,
            position = position,
            durationMs = durationMs,
            artworkPath = "/art/$position.jpg",
            embeddedArt = null
        )

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
}
