package com.jpd.hz.platform.jellyfin

import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.NO_ALBUM_GROUP
import com.jpd.hz.platform.jellyfin.api.MediaItem
import com.jpd.hz.platform.jellyfin.api.MediaSource
import com.jpd.hz.platform.jellyfin.api.NameId
import com.jpd.hz.platform.jellyfin.api.PersonInfo
import com.jpd.hz.platform.jellyfin.api.ServerCatalogue
import com.jpd.hz.platform.jellyfin.api.ServerPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val NO_ALBUM_NAME = "Songs with no album"
private const val TICKS = 2_390_000_000L
private const val TICKS_MS = 239_000L
private const val SIZE = 63_600_000L

/**
 * Jellyfin's fetch as the harness's items and groups: albums, playlists and books, and which
 * parts failed (adapter harness spec, "Fits the harness" and H4). The audio-detail columns went
 * with the old catalogue tables (spec "Data").
 */
class JellyfinCatalogueMappingTest {

    private fun audio(
        id: String,
        albumId: String? = "alb1",
        album: String? = "Album One",
        albumArtist: String? = "Artist A",
        artists: List<String>? = listOf("Artist A"),
        path: String? = "/srv/music/$id.flac",
        dateModified: String? = null,
        sources: List<MediaSource>? = null,
        ticks: Long? = TICKS
    ) = MediaItem(
        id = id,
        name = "Track $id",
        type = "Audio",
        albumArtist = albumArtist,
        album = album,
        albumId = albumId,
        trackNumber = 1,
        discNumber = 1,
        runTimeTicks = ticks,
        path = path,
        mediaSources = sources,
        dateModified = dateModified,
        year = 2018,
        artists = artists,
        albumArtists = albumArtist?.let { listOf(NameId(it, "art-$it")) }
    )

    private fun item(id: String, type: String = "Audio") =
        MediaItem(id = id, name = "Item $id", type = type)

    private fun catalogueOf(
        audio: List<MediaItem> = emptyList(),
        playlists: List<ServerPlaylist> = emptyList(),
        books: List<MediaItem> = emptyList()
    ) = JellyfinCatalogueMapping.sourceCatalogueOf(
        ServerCatalogue(audio = audio, playlists = playlists, books = books),
        NO_ALBUM_NAME
    )

    @Test
    fun `an item has its ID, its layout path, its label and our tag fields`() {
        val song = audio("t1")

        val item = JellyfinCatalogueMapping.itemOf(song)

        assertEquals("t1", item.id)
        assertEquals(JellyfinLayout.pathOf(song), item.path)
        assertEquals("Music/Artist A/Album One/t1.flac", item.path)
        assertEquals(ItemKind.MUSIC, item.kind)
        assertEquals("Artist A - Track t1", item.label)
        assertEquals(JellyfinTagMapping.fieldsOf(song), item.fields)
    }

    @Test
    fun `the version is the server path and its date, so either change downloads again`() {
        val dated = audio("t1", dateModified = "2026-10-01T10:00:00Z")

        assertEquals(
            "/srv/music/t1.flac|2026-10-01T10:00:00Z",
            JellyfinCatalogueMapping.itemOf(dated).version
        )
        assertEquals("/srv/music/t2.flac", JellyfinCatalogueMapping.itemOf(audio("t2")).version)
        assertNull(JellyfinCatalogueMapping.itemOf(audio("t3", path = null)).version)
    }

    @Test
    fun `the label uses the track artist first`() {
        val featured = audio("t1", albumArtist = "Various Artists", artists = listOf("A", "B"))

        assertEquals("A - Track t1", JellyfinCatalogueMapping.itemOf(featured).label)
    }

    @Test
    fun `duration comes from run time ticks and stays null without them`() {
        assertEquals(TICKS_MS, JellyfinCatalogueMapping.itemOf(audio("t1")).durationMs)
        assertNull(JellyfinCatalogueMapping.itemOf(audio("t2", ticks = null)).durationMs)
    }

    @Test
    fun `size comes from the first media source and stays null without one`() {
        val sources = listOf(
            MediaSource(id = "s1", container = "flac", size = SIZE),
            MediaSource(id = "s2", container = "mp3", size = 1L)
        )

        assertEquals(SIZE, JellyfinCatalogueMapping.itemOf(audio("t1", sources = sources)).size)
        assertNull(JellyfinCatalogueMapping.itemOf(audio("t2")).size)
    }

    @Test
    fun `albums are listed once each, in the order of their first song`() {
        val catalogue = catalogueOf(
            audio = listOf(
                audio("t1", albumId = "alb2", album = "Two", albumArtist = "B"),
                audio("t2", albumId = "alb1", album = "One", albumArtist = null),
                audio("t3", albumId = "alb2", album = "Two", albumArtist = "B")
            )
        )

        assertEquals(
            listOf(
                ChoiceGroup("alb2", ChoiceKind.ALBUM, "Two", "B", listOf("t1", "t3")),
                ChoiceGroup("alb1", ChoiceKind.ALBUM, "One", "Artist A", listOf("t2"))
            ),
            catalogue.groups
        )
        assertEquals(listOf("t1", "t2", "t3"), catalogue.items.map { it.id })
    }

    @Test
    fun `songs with no album are kept, as one album group named for them`() {
        val catalogue = catalogueOf(
            audio = listOf(
                audio("t1", albumId = null, album = null),
                audio("t2"),
                audio("t3", albumId = null, album = null)
            )
        )

        assertEquals(listOf("t1", "t2", "t3"), catalogue.items.map { it.id })
        assertEquals(
            ChoiceGroup(NO_ALBUM_GROUP, ChoiceKind.ALBUM, NO_ALBUM_NAME, null, listOf("t1", "t3")),
            catalogue.groups.first()
        )
    }

    @Test
    fun `playlists keep their audio entries in order with repeats, and drop ones without audio`() {
        val catalogue = catalogueOf(
            playlists = listOf(
                ServerPlaylist(
                    item("p1", "Playlist"),
                    listOf(item("t3"), item("v1", "Video"), item("t1"), item("t3"))
                ),
                ServerPlaylist(item("p2", "Playlist"), listOf(item("v2", "Video")))
            )
        )

        assertEquals(
            listOf(
                ChoiceGroup("p1", ChoiceKind.PLAYLIST, "Item p1", null, listOf("t3", "t1", "t3"))
            ),
            catalogue.groups
        )
    }

    @Test
    fun `a book is a book item and a group of its own, with its author as the detail`() {
        val dune = MediaItem(
            id = "b1",
            name = "Dune",
            type = "AudioBook",
            runTimeTicks = 36_000_000_000L,
            people = listOf(PersonInfo("Frank Herbert", "Author")),
            mediaSources = listOf(MediaSource(id = "s1", container = "m4b", size = 412_000_000L))
        )

        val catalogue = catalogueOf(audio = listOf(audio("t1")), books = listOf(dune))

        val book = catalogue.items.last()
        assertEquals("b1", book.id)
        assertEquals(ItemKind.BOOK, book.kind)
        assertEquals(3_600_000L, book.durationMs)
        assertEquals(412_000_000L, book.size)
        assertEquals(
            ChoiceGroup("b1", ChoiceKind.BOOK, "Dune", "Frank Herbert", listOf("b1")),
            catalogue.groups.last()
        )
    }

    @Test
    fun `failed parts of the fetch become failed kinds and failed groups`() {
        val fetched = ServerCatalogue(
            audio = emptyList(),
            playlists = emptyList(),
            playlistsFailed = true,
            failedPlaylistIds = setOf("p2"),
            booksFailed = true
        )

        val catalogue = JellyfinCatalogueMapping.sourceCatalogueOf(fetched, NO_ALBUM_NAME)

        assertEquals(setOf(ChoiceKind.PLAYLIST, ChoiceKind.BOOK), catalogue.failedKinds)
        assertEquals(setOf("p2"), catalogue.failedGroupIds)
        assertEquals(emptySet<ChoiceKind>(), catalogueOf().failedKinds)
        assertEquals(emptySet<String>(), catalogueOf().failedGroupIds)
    }
}
