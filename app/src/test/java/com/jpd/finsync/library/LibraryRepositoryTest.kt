package com.jpd.finsync.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.db.SyncedTrack
import com.jpd.finsync.model.MediaItem
import com.jpd.finsync.model.NameId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PREFS = "settings"
private const val SELECTED_ALBUMS = "selected_albums"

/** Runs the repository's SQL and Kotlin rules against an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {

    private val kurt = NameId(name = "Kurt Vile", id = "kurt")
    private val kim = NameId(name = "Kim Gordon", id = "kim")
    private val lucy = NameId(name = "Lucy Harrow", id = "lucy")
    private val rock = NameId(name = "Indie Rock", id = "rock")

    private lateinit var context: Context
    private lateinit var database: SyncDatabase
    private lateinit var repository: LibraryRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LibraryRepository(context, database.catalogueDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun audio(
        id: String,
        albumId: String?,
        title: String = "Track $id",
        album: String? = albumId?.let { "Album $it" },
        year: Int? = null,
        albumArtists: List<NameId> = emptyList(),
        artists: List<NameId> = emptyList(),
        genres: List<NameId> = emptyList(),
        number: Int? = null
    ) = MediaItem(
        id = id,
        name = title,
        type = "Audio",
        album = album,
        albumId = albumId,
        trackNumber = number,
        discNumber = 1,
        year = year,
        artistItems = artists,
        albumArtists = albumArtists,
        genreItems = genres
    )

    /** Writes the catalogue, then marks [downloaded] as synced. */
    private suspend fun write(items: List<MediaItem>, downloaded: List<String>) {
        repository.writeCatalogue(items)
        val albumIds = items.associate { it.id to it.albumId }
        for (itemId in downloaded) {
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

    private fun select(vararg albumIds: String) {
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
    fun `an album artist is listed only with a visible album`() {
        runBlocking {
            write(
                listOf(
                    audio("t1", "alb1", albumArtists = listOf(kurt)),
                    audio("t2", "alb2", albumArtists = listOf(kim)),
                    audio("t3", "alb3", albumArtists = listOf(lucy))
                ),
                downloaded = listOf("t1", "t3")
            )
            select("alb1", "alb2")
            assertEquals(listOf("kurt"), repository.albumArtists().first().map { it.artistId })
        }
    }

    @Test
    fun `a joint album appears under both artists`() {
        runBlocking {
            write(listOf(audio("t1", "alb1", albumArtists = listOf(kurt, kim))), listOf("t1"))
            val artists = repository.albumArtists().first()
            assertEquals(listOf("kim", "kurt"), artists.map { it.artistId })
            assertEquals(listOf(1, 1), artists.map { it.albumCount })
            val kurtPage = checkNotNull(repository.artist("kurt").first())
            val kimPage = checkNotNull(repository.artist("kim").first())
            assertEquals(listOf("alb1"), kurtPage.albums.map { it.albumId })
            assertEquals(listOf("alb1"), kimPage.albums.map { it.albumId })
        }
    }

    @Test
    fun `artist songs include a featured track elsewhere and skip tracks not downloaded`() {
        runBlocking {
            write(
                listOf(
                    audio("t1", "alb1", year = 2018, albumArtists = listOf(kurt), number = 1),
                    audio("t2", "alb1", year = 2018, albumArtists = listOf(kurt), number = 2),
                    audio(
                        "t3", "alb2", year = 2020,
                        albumArtists = listOf(kim), artists = listOf(kim, kurt)
                    )
                ),
                downloaded = listOf("t1", "t3")
            )
            val page = checkNotNull(repository.artist("kurt").first())
            assertEquals(listOf("t3", "t1"), page.songs.map { it.itemId })
            assertEquals(listOf("alb1"), page.albums.map { it.albumId })
            assertTrue(page.showsAllSongs)
        }
    }

    @Test
    fun `a genre lists the albums and songs tagged with it`() {
        runBlocking {
            write(
                listOf(
                    audio("t1", "alb1", year = 2018, genres = listOf(rock)),
                    audio("t2", "alb1", year = 2018),
                    audio("t3", "alb2", year = 2020, genres = listOf(rock)),
                    audio("t4", null, title = "Loose", genres = listOf(rock))
                ),
                downloaded = listOf("t1", "t2", "t3", "t4")
            )
            val page = checkNotNull(repository.genre("rock").first())
            assertEquals("Indie Rock", page.name)
            assertEquals(listOf("alb2", "alb1"), page.albums.map { it.albumId })
            assertEquals(listOf("t3", "t1", "t4"), page.songs.map { it.itemId })
            val genre = repository.genres().first().single()
            assertEquals(2, genre.albumCount)
            assertEquals(3, genre.songCount)
        }
    }

    @Test
    fun `group pages put albums newest first and undated albums last`() {
        runBlocking {
            write(
                listOf(
                    audio("t1", "old", album = "Old", year = 2015, albumArtists = listOf(kurt)),
                    audio("t2", "none", album = "Undated", albumArtists = listOf(kurt), number = 2),
                    audio("t3", "none", album = "Undated", albumArtists = listOf(kurt), number = 1),
                    audio("t4", "new", album = "New", year = 2020, albumArtists = listOf(kurt))
                ),
                downloaded = listOf("t1", "t2", "t3", "t4")
            )
            val page = checkNotNull(repository.artist("kurt").first())
            assertEquals(listOf("new", "old", "none"), page.albums.map { it.albumId })
            assertEquals(listOf("t4", "t1", "t3", "t2"), page.songs.map { it.itemId })
        }
    }

    @Test
    fun `songs are A to Z by title ignoring case`() {
        runBlocking {
            write(
                listOf(
                    audio("t1", "alb1", title = "beta"),
                    audio("t2", "alb1", title = "Alpha"),
                    audio("t3", null, title = "Gamma"),
                    audio("t4", "alb2", title = "Delta")
                ),
                downloaded = listOf("t1", "t2", "t3", "t4")
            )
            select("alb1")
            assertEquals(
                listOf("Alpha", "beta", "Gamma"),
                repository.songs().first().map { it.title }
            )
        }
    }

    @Test
    fun `home counts follow the album selection`() {
        runBlocking {
            val jazz = NameId(name = "Jazz", id = "jazz")
            write(
                listOf(
                    audio("t1", "alb1", albumArtists = listOf(kurt), genres = listOf(rock)),
                    audio("t2", "alb2", albumArtists = listOf(kim), genres = listOf(jazz)),
                    audio("t3", null, title = "Loose")
                ),
                downloaded = listOf("t1", "t2", "t3")
            )
            select("alb1")
            assertEquals(1, repository.albumArtistCount().first())
            assertEquals(1, repository.genreCount().first())
            assertEquals(2, repository.songCount().first())
        }
    }

    @Test
    fun `playable tracks keep the order asked beyond 999 ids`() {
        runBlocking {
            val ids = (1..1_200).map { "t$it" }
            write(ids.map { audio(it, "alb1") }, downloaded = ids)
            val asked = ids.reversed() + "missing"
            val sources = repository.playableTracks(asked)
            assertEquals(asked.dropLast(1), sources.dropLast(1).map { it?.track?.itemId })
            assertNull(sources.last())
        }
    }

    @Test
    fun `clearing the catalogue empties all seven tables`() {
        runBlocking {
            write(
                listOf(
                    audio(
                        "t1", "alb1",
                        albumArtists = listOf(kurt), artists = listOf(kim), genres = listOf(rock)
                    )
                ),
                downloaded = listOf("t1")
            )
            val tables = listOf(
                "catalogue_albums",
                "catalogue_tracks",
                "catalogue_artists",
                "catalogue_album_artists",
                "catalogue_track_artists",
                "catalogue_genres",
                "catalogue_track_genres"
            )
            assertTrue(tables.all { countRows(it) > 0 })
            repository.clearCatalogue()
            assertEquals(tables.map { 0 }, tables.map { countRows(it) })
        }
    }
}
