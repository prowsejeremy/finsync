package com.jpd.hz.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.db.CatalogueDao
import com.jpd.hz.db.CataloguePlaylistItem
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.db.TrackAlbumRow
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.MediaSource
import com.jpd.hz.model.ServerCatalogue
import com.jpd.hz.model.ServerPlaylist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val AUDIO = "Audio"
private const val HURRY_BYTES = 1_234L

/** Runs the Jellyfin adapter's catalogue against an in-memory sync database. */
@RunWith(RobolectricTestRunner::class)
class JellyfinCatalogueTest {

    private lateinit var database: SyncDatabase
    private lateinit var dao: CatalogueDao
    private lateinit var catalogue: JellyfinCatalogue

    private val tracks = listOf(audio("t1", "alb1"), audio("t2", "alb1"), audio("t3", null))
    private val hurry = book("b1", "Hurry", size = HURRY_BYTES)
    private val atomic = book("b2", "atomic Habits")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.catalogueDao()
        catalogue = JellyfinCatalogue(context, dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun audio(id: String, albumId: String?) =
        MediaItem(id = id, name = "Track $id", type = AUDIO, albumId = albumId)

    private fun book(id: String, name: String, size: Long? = null) = MediaItem(
        id = id,
        name = name,
        type = "AudioBook",
        albumArtist = "Author $id",
        mediaSources = size?.let { listOf(MediaSource(id = "source-$id", size = it)) }
    )

    private fun playlist(id: String, name: String, vararg entries: MediaItem) =
        ServerPlaylist(MediaItem(id = id, name = name, type = "Playlist"), entries.toList())

    @Test
    fun `a write stores tracks, a track with no album included`() = runBlocking {
        catalogue.write(tracks)

        assertEquals(
            listOf(
                TrackAlbumRow("t1", "alb1"),
                TrackAlbumRow("t2", "alb1"),
                TrackAlbumRow("t3", null)
            ),
            dao.trackAlbums().sortedBy { it.itemId }
        )
    }

    @Test
    fun `a write keeps playlist entries in server order, repeats kept, non-audio dropped`() =
        runBlocking {
            val video = MediaItem(id = "v1", name = "Video", type = "Video")
            catalogue.write(
                tracks,
                playlists = listOf(
                    playlist("p1", "Mix", tracks[1], video, tracks[0], tracks[1])
                )
            )

            assertEquals(
                listOf(
                    CataloguePlaylistItem("p1", 0, "t2"),
                    CataloguePlaylistItem("p1", 1, "t1"),
                    CataloguePlaylistItem("p1", 2, "t2")
                ),
                dao.allPlaylistItems()
            )
        }

    @Test
    fun `a write stores books`() = runBlocking {
        catalogue.write(tracks, books = listOf(hurry, atomic))

        val books = dao.allBooks().sortedBy { it.bookId }
        assertEquals(listOf("b1", "b2"), books.map { it.bookId })
        assertEquals(listOf("Hurry", "atomic Habits"), books.map { it.name })
        assertEquals("Author b1", books[0].author)
        assertEquals(HURRY_BYTES, books[0].size)
    }

    @Test
    fun `playlist choices are A to Z with their audio entry counts`() = runBlocking {
        catalogue.write(
            tracks,
            playlists = listOf(
                playlist("p1", "zeta", tracks[0]),
                playlist("p2", "Mix", tracks[0], tracks[1], tracks[0]),
                playlist("p3", "alpha", tracks[2])
            )
        )

        assertEquals(
            listOf(
                PlaylistChoice("p3", "alpha", 1),
                PlaylistChoice("p2", "Mix", 3),
                PlaylistChoice("p1", "zeta", 1)
            ),
            catalogue.playlistChoices().first()
        )
    }

    @Test
    fun `book choices are A to Z`() = runBlocking {
        catalogue.write(tracks, books = listOf(hurry, atomic))

        assertEquals(
            listOf(
                BookChoice("b2", "atomic Habits", "Author b2", null),
                BookChoice("b1", "Hurry", "Author b1", HURRY_BYTES)
            ),
            catalogue.bookChoices().first()
        )
    }

    @Test
    fun `clearing empties tracks, playlists, playlist entries and books`() = runBlocking {
        catalogue.write(tracks, listOf(playlist("p1", "Mix", tracks[0])), listOf(hurry))

        catalogue.clear()

        assertTrue(dao.trackAlbums().isEmpty())
        assertTrue(dao.allPlaylists().isEmpty())
        assertTrue(dao.allPlaylistItems().isEmpty())
        assertTrue(dao.allBooks().isEmpty())
    }

    @Test
    fun `the catalogue is empty until tracks are written`() = runBlocking {
        assertTrue(catalogue.isEmpty())

        catalogue.write(tracks)

        assertFalse(catalogue.isEmpty())
    }

    @Test
    fun `a write whose books failed keeps the previous books and returns them`() = runBlocking {
        catalogue.write(tracks, books = listOf(hurry))

        val rows = catalogue.write(
            ServerCatalogue(
                audio = tracks,
                playlists = emptyList(),
                books = listOf(atomic),
                booksFailed = true
            )
        )

        assertEquals(listOf("b1"), rows.books.map { it.bookId })
        assertEquals(listOf("b1"), dao.allBooks().map { it.bookId })
    }
}
