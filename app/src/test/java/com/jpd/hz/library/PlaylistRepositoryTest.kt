package com.jpd.hz.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.db.SyncedTrack
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.NameId
import com.jpd.hz.model.ServerPlaylist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PREFS = "settings"
private const val SELECTED_ALBUMS = "selected_albums"

/**
 * PlaylistRepository, and the playlist side of LibraryRepository (the catalogue write and the
 * visibility rule), against an in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryTest {

    private val kim = NameId(name = "Kim Gordon", id = "kim")

    private lateinit var context: Context
    private lateinit var database: SyncDatabase
    private lateinit var library: LibraryRepository
    private lateinit var playlists: PlaylistRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        library = LibraryRepository(context, database.catalogueDao())
        playlists = PlaylistRepository(context, database.catalogueDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun audio(id: String, albumId: String?, albumArtists: List<NameId> = emptyList()) =
        MediaItem(
            id = id,
            name = "Track $id",
            type = "Audio",
            album = albumId?.let { "Album $it" },
            albumId = albumId,
            discNumber = 1,
            albumArtists = albumArtists
        )

    private fun playlist(id: String, name: String, vararg entryIds: String) = ServerPlaylist(
        MediaItem(id = id, name = name, type = "Playlist"),
        entryIds.map { MediaItem(id = it, name = "Entry $it", type = "Audio") }
    )

    /** Marks [itemIds] as synced, with their albums. */
    private suspend fun download(items: List<MediaItem>, vararg itemIds: String) {
        val albumIds = items.associate { it.id to it.albumId }
        for (itemId in itemIds) {
            database.syncDao().upsertTrack(
                SyncedTrack(
                    itemId = itemId,
                    localPath = "/music/$itemId.flac",
                    serverPath = null,
                    albumId = albumIds[itemId],
                    fileSize = 1L
                )
            )
        }
    }

    private fun selectAlbums(vararg albumIds: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet(SELECTED_ALBUMS, albumIds.toSet())
            .commit()
    }

    private fun countRows(table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    @Test
    fun `playlist entries keep server order and repeats, and skip songs not downloaded`() {
        runBlocking {
            val items = listOf(audio("t1", "alb1"), audio("t2", "alb1"), audio("t3", "alb2"))
            library.writeCatalogue(items, listOf(playlist("p1", "Mix", "t3", "t1", "t2", "t3")))
            download(items, "t1", "t3")
            val page = checkNotNull(playlists.playlist("p1").first())
            assertEquals(listOf("t3", "t1", "t3"), page.songs.map { it.itemId })
        }
    }

    @Test
    fun `a playlist-only track makes its album, album artist and song visible`() {
        runBlocking {
            val items = listOf(
                audio("t1", "alb1"),
                audio("t2", "alb2", albumArtists = listOf(kim)),
                audio("t3", "alb2", albumArtists = listOf(kim))
            )
            library.writeCatalogue(items, listOf(playlist("p1", "Mix", "t2")))
            download(items, "t1", "t2")
            selectAlbums("alb1")
            playlists.setSelectedIds(setOf("p1"))
            val albums = library.albums().first()
            assertEquals(listOf("alb1", "alb2"), albums.map { it.albumId })
            assertEquals(1, albums.last().downloadedTrackCount)
            assertEquals(listOf("kim"), library.albumArtists().first().map { it.artistId })
            assertEquals(listOf("t1", "t2"), library.songs().first().map { it.itemId })
        }
    }

    @Test
    fun `Playlists lists selected playlists with a downloaded song, A to Z`() {
        runBlocking {
            val items = listOf(audio("t1", "alb1"), audio("t2", "alb1"))
            library.writeCatalogue(
                items,
                listOf(
                    playlist("p1", "Zed", "t1"),
                    playlist("p2", "alpha", "t2"),
                    playlist("p3", "Beta", "t1"),
                    playlist("p4", "Gamma", "t1")
                )
            )
            download(items, "t1")
            playlists.setSelectedIds(setOf("p1", "p2", "p3"))
            assertEquals(listOf("p3", "p1"), playlists.playlists().first().map { it.playlistId })
            assertEquals(2, playlists.playlistCount().first())
        }
    }

    @Test
    fun `clearing the catalogue empties the playlist tables`() {
        runBlocking {
            val items = listOf(audio("t1", "alb1"))
            library.writeCatalogue(items, listOf(playlist("p1", "Mix", "t1")))
            download(items, "t1")
            val tables = listOf("catalogue_playlists", "catalogue_playlist_items")
            assertTrue(tables.all { countRows(it) > 0 })
            library.clearCatalogue()
            assertEquals(listOf(0, 0), tables.map { countRows(it) })
        }
    }
}
