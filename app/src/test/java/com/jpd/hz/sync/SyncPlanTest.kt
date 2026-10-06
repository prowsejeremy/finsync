package com.jpd.hz.sync

import com.jpd.hz.library.playlistRowsFrom
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.PersonInfo
import com.jpd.hz.model.ServerCatalogue
import com.jpd.hz.model.ServerPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncPlanTest {

    private fun audio(id: String, albumId: String?, album: String? = albumId?.let { "Album $it" }) =
        MediaItem(
            id = id,
            name = "Track $id",
            type = "Audio",
            albumArtist = "Kurt Vile",
            album = album,
            albumId = albumId,
            path = "/srv/music/$id.flac"
        )

    private fun book(id: String) = MediaItem(
        id = id,
        name = "Book $id",
        type = "AudioBook",
        albumId = "bookAlbum",
        path = "/srv/books/$id.m4b"
    )

    private fun playlist(id: String, vararg entryIds: String) = ServerPlaylist(
        MediaItem(id = id, name = "Playlist $id", type = "Playlist"),
        entryIds.map { MediaItem(id = it, name = "Entry $it", type = "Audio") }
    )

    // The rows the catalogue write returns; sync plans playlists from these (decision 3).
    private fun rows(vararg playlists: ServerPlaylist) = playlistRowsFrom(playlists.toList())

    private fun selection(
        albums: Set<String> = setOf("alb1"),
        playlists: Set<String> = emptySet(),
        books: Set<String> = emptySet()
    ) = SyncSelection(albums, playlists, books)

    @Test
    fun `albums come first, then playlist-only tracks in playlist order, without repeats`() {
        val catalogue = ServerCatalogue(
            audio = listOf(
                audio("t1", "alb1"), audio("t2", "alb1"), audio("t3", "alb2"), audio("t4", "alb3")
            ),
            playlists = emptyList()
        )
        val written = rows(playlist("p1", "t4", "t1", "t3", "t4"), playlist("p2", "t2"))
        val plan = syncPlanOf(catalogue, written, selection(playlists = setOf("p1", "gone")))
        assertEquals(listOf("t1", "t2", "t4", "t3"), plan.tracks.map { it.id })
        assertEquals(setOf("p1"), plan.playlistIds)
    }

    @Test
    fun `empty playlist and book selections mean none`() {
        val catalogue = ServerCatalogue(
            audio = listOf(audio("t1", "alb1"), audio("t2", "alb2")),
            playlists = emptyList(),
            books = listOf(book("b1"))
        )
        val plan = syncPlanOf(catalogue, rows(playlist("p1", "t2")), selection())
        assertEquals(listOf("t1"), plan.tracks.map { it.id })
        assertTrue(plan.books.isEmpty())
        assertTrue(plan.playlistIds.isEmpty())
    }

    @Test
    fun `books come after the music and lose their album`() {
        val catalogue = ServerCatalogue(
            audio = listOf(audio("t1", "alb1")),
            playlists = emptyList(),
            books = listOf(book("b1"), book("b2"))
        )
        val plan = syncPlanOf(catalogue, rows(), selection(books = setOf("b2")))
        assertEquals(listOf("t1", "b2"), plan.items.map { it.id })
        assertNull(plan.books.single().albumId)
    }

    @Test
    fun `a specific album selection skips tracks without an album, and everything keeps them`() {
        val catalogue = ServerCatalogue(
            audio = listOf(audio("t1", "alb1"), audio("t2", null)),
            playlists = emptyList()
        )
        assertEquals(listOf("t1"), syncPlanOf(catalogue, rows(), selection()).tracks.map { it.id })
        assertEquals(
            listOf("t1", "t2"),
            syncPlanOf(catalogue, rows(), selection(albums = emptySet())).tracks.map { it.id }
        )
        assertEquals(
            listOf("t1", "t2"),
            syncPlanOf(catalogue, rows(), selection(albums = setOf("all"))).tracks.map { it.id }
        )
    }

    @Test
    fun `failed fetches count once for each selected playlist or book they cover`() {
        val listsFailed = ServerCatalogue(
            audio = emptyList(),
            playlists = emptyList(),
            playlistsFailed = true,
            booksFailed = true
        )
        val chosen = selection(playlists = setOf("p1", "p2"), books = setOf("b1", "b2"))
        assertEquals(4, failedFetchCount(listsFailed, chosen))
        val oneFailed = ServerCatalogue(
            audio = emptyList(),
            playlists = emptyList(),
            failedPlaylistIds = setOf("p2", "p9")
        )
        assertEquals(1, failedFetchCount(oneFailed, selection(playlists = setOf("p1", "p2"))))
        assertEquals(0, failedFetchCount(oneFailed, selection()))
    }

    @Test
    fun `files to keep are each track and its album's folder art, under Music`() {
        val plan = SyncPlan(
            tracks = listOf(audio("t1", "alb1", album = "Bottle It In")),
            books = emptyList(),
            playlistIds = emptySet()
        )
        assertEquals(
            setOf(
                "/sync/Music/Kurt Vile/Bottle It In/t1.flac",
                "/sync/Music/Kurt Vile/Bottle It In/folder.jpg"
            ),
            filesToKeep(File("/sync"), plan)
        )
    }

    @Test
    fun `a book keeps its file and cover in Audiobooks, author and title folders`() {
        val book = MediaItem(
            id = "b1",
            name = "Project Hail Mary",
            type = "AudioBook",
            path = "/srv/books/phm.m4b",
            people = listOf(PersonInfo("Andy Weir", "Author"))
        )
        val plan = SyncPlan(tracks = emptyList(), books = listOf(book), playlistIds = emptySet())
        assertEquals(
            setOf(
                "/sync/Audiobooks/Andy Weir/Project Hail Mary/phm.m4b",
                "/sync/Audiobooks/Andy Weir/Project Hail Mary/folder.jpg"
            ),
            filesToKeep(File("/sync"), plan)
        )
    }

    @Test
    fun `a book list that didn't load keeps each synced book's file and cover`() {
        assertEquals(
            setOf(
                "/sync/Audiobooks/Andy Weir/Project Hail Mary/phm.m4b",
                "/sync/Audiobooks/Andy Weir/Project Hail Mary/folder.jpg"
            ),
            bookFilesAt(listOf("/sync/Audiobooks/Andy Weir/Project Hail Mary/phm.m4b"))
        )
    }
}
