package com.jpd.hz.adapter.run

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.adapter.Availability
import com.jpd.hz.adapter.CatalogueResult
import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ExtraFile
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.Source
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.SourceItem
import com.jpd.hz.adapter.db.SyncDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream

private const val KURAGE = "jellyfin:3f2a"
private const val HOME = "plex:77b0"
private const val HURRY_BYTES = 1_234L
private const val SONG_BYTES = 100L

/**
 * Each connection's stored catalogue against an in-memory sync database: writes replace only
 * their own connection's rows, failed parts keep theirs, and the choice screens' lists (adapter
 * harness spec, "Data" and Testing 6).
 */
@RunWith(RobolectricTestRunner::class)
class CatalogueTest {

    private lateinit var database: SyncDatabase
    private lateinit var catalogue: Catalogue

    private val songs = listOf(song("t1"), song("t2"), song("t3"))
    private val albums = listOf(
        group("alb1", ChoiceKind.ALBUM, "Discovery", "Daft Punk", "t1", "t2"),
        group("no-album", ChoiceKind.ALBUM, "Songs with no album", null, "t3")
    )
    private val hurry = book("b1", HURRY_BYTES)
    private val atomic = book("b2", null)
    private val books = listOf(
        group("b1", ChoiceKind.BOOK, "Hurry", "Author b1", "b1"),
        group("b2", ChoiceKind.BOOK, "atomic Habits", "Author b2", "b2")
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        catalogue = Catalogue(database.catalogueDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun song(id: String) = SourceItem(
        id, "Music/$id.flac", "v1", ItemKind.MUSIC, "Artist - $id", null, SONG_BYTES, null
    )

    private fun book(id: String, size: Long?) =
        SourceItem(id, "Audiobooks/$id.m4b", "v1", ItemKind.BOOK, id, null, size, null)

    private fun group(
        id: String,
        kind: ChoiceKind,
        name: String,
        detail: String?,
        vararg itemIds: String
    ) = ChoiceGroup(id, kind, name, detail, itemIds.toList())

    private fun playlist(id: String, name: String, vararg itemIds: String) =
        group(id, ChoiceKind.PLAYLIST, name, null, *itemIds)

    private fun sourceCatalogue(
        items: List<SourceItem> = songs,
        groups: List<ChoiceGroup> = albums,
        failedKinds: Set<ChoiceKind> = emptySet(),
        failedGroupIds: Set<String> = emptySet()
    ) = SourceCatalogue(items, groups, failedKinds, failedGroupIds)

    @Test
    fun `a write stores the items and groups, read back as they were`() = runBlocking {
        val mix = playlist("p1", "Mix", "t2", "t1", "t2")
        val written = sourceCatalogue(
            items = songs + hurry,
            groups = albums + mix + books.first()
        )

        val returned = catalogue.write(KURAGE, written)
        val stored = catalogue.stored(KURAGE)

        assertEquals(returned, stored)
        assertEquals(written.groups, stored.groups)
        assertEquals(listOf("t2", "t1", "t2"), stored.groups.single { it.id == "p1" }.itemIds)
        assertEquals(written.items.map(::storedItemOf), stored.items)
        assertEquals(ItemKind.BOOK, stored.items.last().kind)
    }

    @Test
    fun `the catalogue is empty until items are written`() = runBlocking {
        assertTrue(catalogue.isEmpty(KURAGE))
        assertEquals(StoredCatalogue.EMPTY, catalogue.stored(KURAGE))

        catalogue.write(KURAGE, sourceCatalogue())

        assertFalse(catalogue.isEmpty(KURAGE))
        assertTrue(catalogue.isEmpty(HOME))
    }

    @Test
    fun `a write replaces its own connection's rows and leaves another's alone`() = runBlocking {
        catalogue.write(KURAGE, sourceCatalogue())
        catalogue.write(HOME, sourceCatalogue(items = listOf(hurry), groups = books.take(1)))

        catalogue.write(KURAGE, sourceCatalogue(items = listOf(song("t9")), groups = emptyList()))

        assertEquals(listOf("t9"), catalogue.stored(KURAGE).items.map { it.id })
        assertTrue(catalogue.stored(KURAGE).groups.isEmpty())
        assertEquals(listOf("b1"), catalogue.stored(HOME).items.map { it.id })
        assertEquals(books.take(1), catalogue.stored(HOME).groups)
    }

    @Test
    fun `a write whose books failed keeps the previous books and returns them`() = runBlocking {
        catalogue.write(KURAGE, sourceCatalogue(songs + hurry, albums + books.first()))

        // A kind that didn't load comes with none of its groups or items, as Jellyfin's does.
        val returned = catalogue.write(
            KURAGE,
            sourceCatalogue(items = songs, groups = albums, failedKinds = setOf(ChoiceKind.BOOK))
        )

        val bookIds = { stored: StoredCatalogue ->
            stored.groups.filter { it.kind == ChoiceKind.BOOK }.map { it.id }
        }
        assertEquals(listOf("b1"), bookIds(returned))
        assertEquals(listOf("b1"), bookIds(catalogue.stored(KURAGE)))
        assertEquals(storedItemOf(hurry), catalogue.stored(KURAGE).items.last())
    }

    @Test
    fun `a write whose playlist failed keeps that playlist's previous rows`() = runBlocking {
        val old = playlist("p1", "Old", "t1")
        catalogue.write(KURAGE, sourceCatalogue(groups = albums + old))

        catalogue.write(
            KURAGE,
            sourceCatalogue(
                groups = albums + playlist("p2", "New", "t2"),
                failedGroupIds = setOf("p1")
            )
        )

        val playlists = catalogue.stored(KURAGE).groups.filter { it.kind == ChoiceKind.PLAYLIST }
        assertEquals(listOf("p2", "p1"), playlists.map { it.id })
        assertEquals(old, playlists.last())
    }

    @Test
    fun `a refresh's write is dropped once its sign-in has gone`() = runBlocking {
        val written = catalogue.writeWhileSignedIn(KURAGE, sourceCatalogue()) { false }

        assertFalse(written)
        assertTrue(catalogue.isEmpty(KURAGE))
    }

    @Test
    fun `a refresh's write lands while its sign-in holds`() = runBlocking {
        val written = catalogue.writeWhileSignedIn(KURAGE, sourceCatalogue()) { true }

        assertTrue(written)
        assertEquals(songs.map { it.id }, catalogue.stored(KURAGE).items.map { it.id })
    }

    @Test
    fun `a refresh stores what the source offers, and a failed fetch writes nothing`() =
        runBlocking {
            val offered = FixedSource(CatalogueResult.Success(sourceCatalogue()))

            assertTrue(catalogue.refresh(KURAGE, offered) { true })
            assertEquals(albums, catalogue.stored(KURAGE).groups)

            val failed = FixedSource(CatalogueResult.Failure("Server unreachable"))
            assertFalse(catalogue.refresh(HOME, failed) { true })
            assertTrue(catalogue.isEmpty(HOME))
        }

    @Test
    fun `clearing empties only that connection's items, groups and entries`() = runBlocking {
        val mix = playlist("p1", "Mix", "t1")
        catalogue.write(KURAGE, sourceCatalogue(songs + hurry, albums + mix))
        catalogue.write(HOME, sourceCatalogue())

        catalogue.clear(KURAGE)

        assertEquals(StoredCatalogue.EMPTY, catalogue.stored(KURAGE))
        assertEquals(albums, catalogue.stored(HOME).groups)
    }

    @Test
    fun `clearing does nothing once the connection has signed in again`() = runBlocking {
        catalogue.write(KURAGE, sourceCatalogue())

        catalogue.clear(KURAGE) { true }

        assertEquals(albums, catalogue.stored(KURAGE).groups)
    }

    @Test
    fun `choices list one kind A to Z ignoring case, with item counts and sizes`() = runBlocking {
        catalogue.write(
            KURAGE,
            sourceCatalogue(
                items = songs + hurry + atomic,
                groups = albums + books + listOf(
                    playlist("p1", "zeta", "t1"),
                    playlist("p2", "Mix", "t1", "t2", "t1"),
                    playlist("p3", "alpha", "t3")
                )
            )
        )

        assertEquals(
            listOf(
                GroupChoice("p3", "alpha", null, 1, SONG_BYTES),
                GroupChoice("p2", "Mix", null, 3, 3 * SONG_BYTES),
                GroupChoice("p1", "zeta", null, 1, SONG_BYTES)
            ),
            catalogue.choices(KURAGE, ChoiceKind.PLAYLIST).first()
        )
        assertEquals(
            listOf(
                GroupChoice("b2", "atomic Habits", "Author b2", 1, null),
                GroupChoice("b1", "Hurry", "Author b1", 1, HURRY_BYTES)
            ),
            catalogue.choices(KURAGE, ChoiceKind.BOOK).first()
        )
        assertEquals(
            listOf("Discovery", "Songs with no album"),
            catalogue.choices(KURAGE, ChoiceKind.ALBUM).first().map { it.name }
        )
        assertTrue(catalogue.choices(HOME, ChoiceKind.PLAYLIST).first().isEmpty())
    }

    /** A source whose catalogue is [result]; a refresh reads nothing else. */
    private class FixedSource(private val result: CatalogueResult) : Source {
        override val signInRefused: Flow<Boolean> = flowOf(false)

        override suspend fun checkAvailability() = Availability.AVAILABLE

        override suspend fun catalogue() = result

        override suspend fun open(item: SourceItem): InputStream = error("Not read by a refresh")

        override suspend fun openGroupImage(group: ChoiceGroup): InputStream? = null

        override fun extras(plan: SyncPlan): List<ExtraFile> = emptyList()
    }
}
