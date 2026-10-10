package com.jpd.hz.adapter.run

import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ExtraFile
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.SourceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ONE_MORE_TIME = "Music/Daft Punk/Discovery/01 One More Time.flac"
private const val DISCOVERY_COVER = "Music/Daft Punk/Discovery/folder.jpg"
private const val HAIL_MARY = "Audiobooks/Andy Weir/Project Hail Mary/phm.m4b"
private const val HAIL_MARY_COVER = "Audiobooks/Andy Weir/Project Hail Mary/folder.jpg"
private const val TRACK_MS = 215_500L

/**
 * What cleanup keeps in a connection's folder, and the playlist files a run writes (adapter
 * harness spec, "The sync run", steps 5 and 6, and "The contract").
 */
class KeepSetTest {

    private fun song(id: String, path: String, durationMs: Long? = TRACK_MS) =
        SourceItem(id, path, "v1", ItemKind.MUSIC, "Daft Punk - $id", durationMs, null, null)

    private val oneMoreTime = song("t1", ONE_MORE_TIME)
    private val hailMary =
        SourceItem("b1", HAIL_MARY, "v1", ItemKind.BOOK, "Project Hail Mary", null, null, null)

    private fun group(id: String, kind: ChoiceKind, name: String, vararg itemIds: String) =
        ChoiceGroup(id, kind, name, null, itemIds.toList())

    private val discovery = group("alb1", ChoiceKind.ALBUM, "Discovery", "t1", "t2")
    private val book = group("b1", ChoiceKind.BOOK, "Project Hail Mary", "b1")

    private fun plan(
        items: List<SourceItem> = emptyList(),
        playlists: List<PlannedPlaylist> = emptyList(),
        covers: List<PlannedCover> = emptyList()
    ) = SyncPlan(items, playlists, covers)

    private fun extra(path: String) = ExtraFile(path) { null }

    @Test
    fun `the keep set holds each planned item and its album's cover`() {
        val cover = PlannedCover(discovery, listOf(oneMoreTime))

        val keep = keepSetOf(plan(listOf(oneMoreTime), covers = listOf(cover)), emptyList())

        assertEquals(setOf(ONE_MORE_TIME, DISCOVERY_COVER), keep)
    }

    @Test
    fun `a book keeps its file and the cover beside it`() {
        val keep = keepSetOf(
            plan(listOf(hailMary), covers = listOf(PlannedCover(book, listOf(hailMary)))),
            emptyList()
        )

        assertEquals(setOf(HAIL_MARY, HAIL_MARY_COVER), keep)
    }

    @Test
    fun `a cover is kept beside each planned item of its group, even in another folder`() {
        val disc2 = song("t3", "Music/Daft Punk/Discovery/CD2/01 Digital Love.flac")
        val items = listOf(oneMoreTime, disc2)
        val cover = PlannedCover(discovery, items)

        val keep = keepSetOf(plan(items, covers = listOf(cover)), emptyList())

        assertTrue(DISCOVERY_COVER in keep)
        assertTrue("Music/Daft Punk/Discovery/CD2/folder.jpg" in keep)
    }

    @Test
    fun `an item with no cover group keeps no cover`() {
        val keep = keepSetOf(plan(listOf(song("f1", "Shared/x.flac"))), emptyList())

        assertEquals(setOf("Shared/x.flac"), keep)
    }

    @Test
    fun `the keep set holds the extras and each playlist's file and cover`() {
        val plan = plan(
            items = listOf(oneMoreTime),
            playlists = listOf(
                PlannedPlaylist(group("p1", ChoiceKind.PLAYLIST, "Road Trip"), listOf(oneMoreTime)),
                PlannedPlaylist(group("p2", ChoiceKind.PLAYLIST, "road trip"), emptyList())
            ),
            covers = listOf(PlannedCover(discovery, listOf(oneMoreTime)))
        )

        val keep = keepSetOf(plan, listOf(extra("Music/Daft Punk/artist.jpg")))

        assertEquals(
            setOf(
                ONE_MORE_TIME,
                DISCOVERY_COVER,
                "Music/Daft Punk/artist.jpg",
                "Playlists/Road Trip.m3u8",
                "Playlists/Road Trip.jpg",
                "Playlists/road trip (2).m3u8",
                "Playlists/road trip (2).jpg"
            ),
            keep
        )
    }

    @Test
    fun `playlists sharing a name, ignoring case, get numbered file names`() {
        val plan = plan(
            playlists = listOf(
                PlannedPlaylist(group("p2", ChoiceKind.PLAYLIST, "road trip"), emptyList()),
                PlannedPlaylist(group("p1", ChoiceKind.PLAYLIST, "Road Trip"), emptyList()),
                PlannedPlaylist(group("p3", ChoiceKind.PLAYLIST, " "), emptyList())
            )
        )

        val names = playlistFileNamesOf(plan)

        assertEquals("Road Trip", names["p1"])
        assertEquals("road trip (2)", names["p2"])
        assertTrue(names.values.all { it.isNotBlank() })
    }

    @Test
    fun `a playlist file points from Playlists to each item, with its length and label`() {
        val untimed = song("t9", "Music/Unknown Artist/Unknown Album/t9.mp3", durationMs = null)
        val playlist = PlannedPlaylist(
            group("p1", ChoiceKind.PLAYLIST, "Road Trip"),
            listOf(oneMoreTime, untimed, oneMoreTime)
        )

        assertEquals(
            "#EXTM3U\n" +
                "#PLAYLIST:Road Trip\n" +
                "#EXTINF:215,Daft Punk - t1\n" +
                "../$ONE_MORE_TIME\n" +
                "#EXTINF:-1,Daft Punk - t9\n" +
                "../Music/Unknown Artist/Unknown Album/t9.mp3\n" +
                "#EXTINF:215,Daft Punk - t1\n" +
                "../$ONE_MORE_TIME\n",
            playlistTextOf(playlist)
        )
    }

    @Test
    fun `only relative paths with no parent segment are safe to write`() {
        assertTrue(isSafePath(ONE_MORE_TIME))
        assertTrue(isSafePath("Music/.hidden/x.flac"))
        assertTrue(isSafePath("Music/a..b/x.flac"))
        assertFalse(isSafePath("/storage/emulated/0/x.flac"))
        assertFalse(isSafePath("../x.flac"))
        assertFalse(isSafePath("Music/../../x.flac"))
        assertFalse(isSafePath(""))
        assertFalse(isSafePath("  "))
    }
}
