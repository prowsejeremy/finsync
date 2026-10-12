package com.jpd.hz.adapter.run

import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.NO_ALBUM_GROUP
import com.jpd.hz.adapter.choices.ALL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Sync card's counts from the stored catalogue and the choices, with no server call (adapter
 * harness spec, "The sync run" and H4: an empty choice now counts nothing).
 */
class SyncCountsTest {

    private fun item(id: String, kind: ItemKind = ItemKind.MUSIC) =
        StoredItem(id, kind, id, null, null)

    private fun group(id: String, kind: ChoiceKind, vararg itemIds: String) =
        ChoiceGroup(id, kind, id, null, itemIds.toList())

    private val songs = listOf(item("t1"), item("t2"), item("t3"), item("t4"))
    private val albums = listOf(
        group("alb1", ChoiceKind.ALBUM, "t1", "t2"),
        group("alb2", ChoiceKind.ALBUM, "t3"),
        group(NO_ALBUM_GROUP, ChoiceKind.ALBUM, "t4")
    )
    private val books = listOf("b1", "b2", "b3")

    private fun counts(
        albumChoice: Set<String>,
        playlistChoice: Set<String> = emptySet(),
        bookChoice: Set<String> = emptySet(),
        playlists: List<ChoiceGroup> = emptyList(),
        catalogueBooks: List<String> = emptyList(),
        synced: Set<String> = emptySet()
    ): SyncCounts = syncCountsOf(
        stored = StoredCatalogue(
            groups = albums + playlists + catalogueBooks.map { group(it, ChoiceKind.BOOK, it) },
            items = songs + catalogueBooks.map { item(it, ItemKind.BOOK) }
        ),
        chosen = mapOf(
            ChoiceKind.ALBUM to albumChoice,
            ChoiceKind.PLAYLIST to playlistChoice,
            ChoiceKind.BOOK to bookChoice
        ),
        syncedIds = synced
    )

    @Test
    fun `all counts every song, a specific choice only its albums', and none counts none`() {
        assertEquals(4, counts(albumChoice = setOf(ALL)).songsTotal)
        assertEquals(2, counts(albumChoice = setOf("alb1")).songsTotal)
        assertEquals(0, counts(albumChoice = emptySet()).songsTotal)
    }

    @Test
    fun `a song with no album counts under all, or when its own group is chosen`() {
        val everything = counts(albumChoice = setOf(ALL), synced = setOf("t4"))
        val specific = counts(albumChoice = setOf("alb1", "alb2"), synced = setOf("t4"))
        val noAlbum = counts(albumChoice = setOf(NO_ALBUM_GROUP), synced = setOf("t4"))

        assertEquals(4, everything.songsTotal)
        assertEquals(1, everything.songsSynced)
        assertEquals(3, specific.songsTotal)
        assertEquals(0, specific.songsSynced)
        assertEquals(1, noAlbum.songsTotal)
        assertEquals(1, noAlbum.songsSynced)
    }

    @Test
    fun `a playlist song also in a chosen album counts once`() {
        val result = counts(
            albumChoice = setOf("alb1"),
            playlistChoice = setOf("p1"),
            playlists = listOf(
                group("p1", ChoiceKind.PLAYLIST, "t1", "t3", "t3"),
                group("p2", ChoiceKind.PLAYLIST, "t4")
            ),
            synced = setOf("t1", "t3")
        )

        // t1 and t2 from the album, t3 once from p1; p2 isn't chosen.
        assertEquals(3, result.songsTotal)
        assertEquals(2, result.songsSynced)
    }

    @Test
    fun `a playlist entry that isn't a catalogue item doesn't count`() {
        val result = counts(
            albumChoice = setOf("alb2"),
            playlistChoice = setOf("p1"),
            playlists = listOf(group("p1", ChoiceKind.PLAYLIST, "gone"))
        )

        assertEquals(1, result.songsTotal)
    }

    @Test
    fun `chosen books count, and only the synced ones as synced`() {
        val result = counts(
            albumChoice = setOf("alb2"),
            bookChoice = setOf("b1", "b2"),
            catalogueBooks = books,
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
    fun `a chosen book missing from the catalogue doesn't count`() {
        val result = counts(
            albumChoice = setOf("alb2"),
            bookChoice = setOf("b1", "gone"),
            catalogueBooks = listOf("b1"),
            synced = setOf("t3", "b1")
        )

        assertEquals(1, result.booksTotal)
        assertTrue(result.allSynced)
    }

    @Test
    fun `nothing chosen counts nothing`() {
        assertEquals(
            SyncCounts.NONE,
            counts(albumChoice = emptySet(), catalogueBooks = books, synced = setOf("t1", "b1"))
        )
        assertEquals(SyncCounts.NONE, counts(albumChoice = setOf("gone"), synced = setOf("t1")))
        assertTrue(SyncCounts.NONE.allSynced)
    }

    @Test
    fun `a kind missing from the choices counts nothing of that kind`() {
        val result = syncCountsOf(
            stored = StoredCatalogue(albums, songs),
            chosen = mapOf(ChoiceKind.PLAYLIST to setOf(ALL)),
            syncedIds = emptySet()
        )

        assertEquals(SyncCounts.NONE, result)
    }
}
