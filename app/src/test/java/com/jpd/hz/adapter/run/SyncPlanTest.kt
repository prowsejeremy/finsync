package com.jpd.hz.adapter.run

import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.NO_ALBUM_GROUP
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.SourceItem
import com.jpd.hz.adapter.choices.ALL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A run's plan: chosen albums and folders in the source's order, then songs only chosen
 * playlists need, then chosen books, each once; the playlists with their repeats; the covers; and
 * how many chosen groups a failed fetch covers (adapter harness spec, "The sync run", step 4, and
 * H4).
 */
class SyncPlanTest {

    private fun song(id: String) =
        SourceItem(id, "Music/$id.flac", "v1", ItemKind.MUSIC, "Artist - $id", null, null, null)

    private fun book(id: String) =
        SourceItem(id, "Audiobooks/$id/$id.m4b", "v1", ItemKind.BOOK, id, null, null, null)

    private fun group(id: String, kind: ChoiceKind, vararg itemIds: String) =
        ChoiceGroup(id, kind, "Name $id", null, itemIds.toList())

    private fun album(id: String, vararg itemIds: String) = group(id, ChoiceKind.ALBUM, *itemIds)

    private fun playlist(id: String, vararg itemIds: String) =
        group(id, ChoiceKind.PLAYLIST, *itemIds)

    private val songs = listOf(song("t1"), song("t2"), song("t3"), song("t4"))
    private val albums = listOf(album("alb1", "t1", "t2"), album("alb2", "t3"), album("alb3", "t4"))

    private fun chosen(
        albums: Set<String> = setOf("alb1"),
        playlists: Set<String> = emptySet(),
        books: Set<String> = emptySet()
    ) = mapOf(
        ChoiceKind.ALBUM to albums,
        ChoiceKind.PLAYLIST to playlists,
        ChoiceKind.BOOK to books
    )

    private fun ids(plan: SyncPlan) = plan.items.map { it.id }

    @Test
    fun `albums come first, then playlist-only songs in playlist order, without repeats`() {
        val groups = albums + playlist("p1", "t4", "t1", "t3", "t4") + playlist("p2", "t2")

        val plan = planOf(groups, songs, chosen(playlists = setOf("p1", "gone")))

        assertEquals(listOf("t1", "t2", "t4", "t3"), ids(plan))
        assertEquals(listOf("p1"), plan.playlists.map { it.group.id })
    }

    @Test
    fun `chosen albums plan in the source's order, not the choice's`() {
        val plan = planOf(albums, songs, chosen(albums = setOf("alb3", "alb1")))

        assertEquals(listOf("t1", "t2", "t4"), ids(plan))
    }

    @Test
    fun `an empty choice means none, for every kind`() {
        val groups = albums + playlist("p1", "t2") + group("b1", ChoiceKind.BOOK, "b1")

        val plan = planOf(groups, songs + book("b1"), chosen(albums = emptySet()))

        assertTrue(plan.items.isEmpty())
        assertTrue(plan.playlists.isEmpty())
        assertTrue(plan.covers.isEmpty())
    }

    @Test
    fun `empty playlist and book choices plan none of them`() {
        val groups = albums + playlist("p1", "t2") + group("b1", ChoiceKind.BOOK, "b1")

        val plan = planOf(groups, songs + book("b1"), chosen())

        assertEquals(listOf("t1", "t2"), ids(plan))
        assertTrue(plan.playlists.isEmpty())
    }

    @Test
    fun `books come after the music`() {
        val groups = listOf(
            group("b1", ChoiceKind.BOOK, "b1"),
            group("b2", ChoiceKind.BOOK, "b2")
        ) + albums
        val items = listOf(book("b1"), book("b2")) + songs

        val plan = planOf(groups, items, chosen(books = setOf("b2")))

        assertEquals(listOf("t1", "t2", "b2"), ids(plan))
    }

    @Test
    fun `folders plan with the albums, before playlists and books`() {
        val groups = listOf(
            group("b1", ChoiceKind.BOOK, "b1"),
            playlist("p1", "t3"),
            group("f1", ChoiceKind.FOLDER, "t2")
        )
        val chosen = mapOf(
            ChoiceKind.FOLDER to setOf(ALL),
            ChoiceKind.PLAYLIST to setOf("p1"),
            ChoiceKind.BOOK to setOf("b1")
        )

        val plan = planOf(groups, songs + book("b1"), chosen)

        assertEquals(listOf("t2", "t3", "b1"), ids(plan))
        // A folder keeps no cover beside its items; its art files are items of their own.
        assertEquals(listOf("b1"), plan.covers.map { it.group.id })
    }

    @Test
    fun `all plans every album, songs with no album included, and new albums too`() {
        val groups = albums + album(NO_ALBUM_GROUP, "t5") + album("added-later", "t6")
        val items = songs + song("t5") + song("t6")

        val plan = planOf(groups, items, chosen(albums = setOf(ALL)))

        assertEquals(listOf("t1", "t2", "t3", "t4", "t5", "t6"), ids(plan))
    }

    @Test
    fun `a specific choice skips songs with no album, unless their group is ticked`() {
        val groups = albums + album(NO_ALBUM_GROUP, "t5")
        val items = songs + song("t5")

        assertEquals(listOf("t1", "t2"), ids(planOf(groups, items, chosen())))
        assertEquals(
            listOf("t1", "t2", "t5"),
            ids(planOf(groups, items, chosen(albums = setOf("alb1", NO_ALBUM_GROUP))))
        )
    }

    @Test
    fun `the plan carries each chosen playlist's songs in order, repeats included`() {
        val groups = albums + playlist("p1", "t3", "t1", "gone", "t3") + playlist("p2", "t2")

        val plan = planOf(groups, songs, chosen(playlists = setOf("p1")))

        val road = plan.playlists.single()
        assertEquals("p1", road.group.id)
        assertEquals("Name p1", road.group.name)
        assertEquals(listOf("t3", "t1", "t3"), road.items.map { it.id })
    }

    @Test
    fun `an item this fetch didn't list isn't planned`() {
        val groups = listOf(album("alb1", "t1", "gone"), playlist("p1", "gone", "t2"))

        val plan = planOf(groups, songs, chosen(playlists = setOf("p1")))

        assertEquals(listOf("t1", "t2"), ids(plan))
    }

    @Test
    fun `covers are the albums and books of planned items, in plan order`() {
        val groups = albums + playlist("p1", "t3") + group("b1", ChoiceKind.BOOK, "b1")
        val chosen = chosen(playlists = setOf("p1"), books = setOf("b1"))

        val plan = planOf(groups, songs + book("b1"), chosen)

        // t3 comes only from a playlist, and still gets its album's cover; alb3 isn't planned.
        assertEquals(
            listOf(
                "alb1" to listOf("t1", "t2"),
                "alb2" to listOf("t3"),
                "b1" to listOf("b1")
            ),
            plan.covers.map { cover -> cover.group.id to cover.items.map { it.id } }
        )
    }

    @Test
    fun `an item in two albums counts for the first one's cover`() {
        val groups = listOf(album("alb1", "t1"), album("alb2", "t1", "t2"))

        val plan = planOf(groups, songs, chosen(albums = setOf(ALL)))

        assertEquals(
            listOf("alb1" to listOf("t1"), "alb2" to listOf("t2")),
            plan.covers.map { cover -> cover.group.id to cover.items.map { it.id } }
        )
    }

    @Test
    fun `failed fetches count once for each chosen playlist or book they cover`() {
        val stored = albums + playlist("p1") + playlist("p2") + playlist("p3") +
            group("b1", ChoiceKind.BOOK, "b1") + group("b2", ChoiceKind.BOOK, "b2")
        val listsFailed = SourceCatalogue(
            items = emptyList(),
            groups = emptyList(),
            failedKinds = setOf(ChoiceKind.PLAYLIST, ChoiceKind.BOOK),
            failedGroupIds = setOf("p1")
        )
        val books = setOf("b1", "b2")
        val some = chosen(playlists = setOf("p1", "p2"), books = books)
        val every = chosen(playlists = setOf(ALL), books = books)

        // p1 is in a failed kind and a failed group, and counts once.
        assertEquals(4, failedChoiceCount(listsFailed, stored, some))
        // All counts every stored group of the kind that didn't load.
        assertEquals(5, failedChoiceCount(listsFailed, stored, every))

        val oneFailed =
            SourceCatalogue(emptyList(), emptyList(), failedGroupIds = setOf("p2", "p9"))
        assertEquals(1, failedChoiceCount(oneFailed, stored, chosen(playlists = setOf("p1", "p2"))))
        // p9, which the stored catalogue doesn't know, counts too when every playlist is chosen.
        assertEquals(2, failedChoiceCount(oneFailed, stored, chosen(playlists = setOf(ALL))))
        assertEquals(0, failedChoiceCount(oneFailed, stored, chosen()))
    }

    @Test
    fun `all over a list that never loaded still counts as a failure`() {
        val failed = SourceCatalogue(emptyList(), emptyList(), failedKinds = setOf(ChoiceKind.BOOK))

        assertEquals(1, failedChoiceCount(failed, albums, chosen(books = setOf(ALL))))
    }

    private val planned = planOf(albums, songs, chosen())

    @Test
    fun `a full fetch with something planned may clean up`() {
        assertTrue(mayCleanUp(SourceCatalogue(songs, albums), albums, chosen(), planned))
    }

    @Test
    fun `a chosen kind that didn't load stops cleanup, even with no stored groups`() {
        // After a sign-out or a rebuilt database, nothing else knows the books' files.
        val booksFailed =
            SourceCatalogue(songs, albums, failedKinds = setOf(ChoiceKind.BOOK))

        assertFalse(mayCleanUp(booksFailed, albums, chosen(books = setOf("b1")), planned))
        assertTrue(mayCleanUp(booksFailed, albums, chosen(), planned))
    }

    @Test
    fun `a chosen group that didn't load stops cleanup, known or not`() {
        val stored = albums + playlist("p1", "t1")
        val oneFailed = SourceCatalogue(songs, albums, failedGroupIds = setOf("p1"))
        val unknownFailed = SourceCatalogue(songs, albums, failedGroupIds = setOf("p9"))

        assertFalse(mayCleanUp(oneFailed, stored, chosen(playlists = setOf("p1")), planned))
        assertTrue(mayCleanUp(oneFailed, stored, chosen(playlists = setOf("p2")), planned))
        assertFalse(mayCleanUp(unknownFailed, stored, chosen(playlists = setOf(ALL)), planned))
    }

    @Test
    fun `an empty plan never cleans up`() {
        val empty = planOf(albums, songs, chosen(albums = setOf("gone")))

        assertFalse(mayCleanUp(SourceCatalogue(songs, albums), albums, chosen(), empty))
    }
}
