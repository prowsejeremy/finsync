package com.jpd.hz.adapter.run

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.choices.ChoiceStore
import com.jpd.hz.adapter.db.SyncDatabase
import com.jpd.hz.adapter.files.FakeTagger
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Two connections signed in at once (adapter harness spec, H3, "Running connections"): each has
 * its own folder, records, catalogue and choices, and signing one out leaves the other alone. The
 * same item ID at both sources never collides.
 */
@RunWith(RobolectricTestRunner::class)
class TwoConnectionsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: SyncDatabase
    private lateinit var choices: ChoiceStore
    private lateinit var library: File
    private lateinit var home: FakePlatform
    private lateinit var work: FakePlatform

    // Both sources call their song "t1", at different paths.
    private val homeSong = song("t1", "Music/Air/Moon Safari/01 La Femme.flac")
    private val workSong = song("t1", "Music/Low/Things We Lost/01 Monkey.flac")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        choices = ChoiceStore(context)
        library = temp.newFolder("library")
        home = FakePlatform(FakeSource(catalogueOf(homeSong)), key = "home")
        work = FakePlatform(FakeSource(catalogueOf(workSong)), key = "work")
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun catalogueOf(vararg songs: com.jpd.hz.adapter.SourceItem) = SourceCatalogue(
        items = songs.toList(),
        groups = listOf(group("album", ChoiceKind.ALBUM, *songs.map { it.id }.toTypedArray()))
    )

    private fun folderOf(platform: FakePlatform) = File(library, platform.key)

    private fun sync(platform: FakePlatform) = runBlocking {
        SyncRun(
            platform.connection.id, database, choices, FakeTagger(), { listOf(home, work) },
            { connection, _ -> File(library, connection.platform) }, { library }, { true }
        ).run()
    }

    private fun chooseAll(platform: FakePlatform) =
        choices.setChosen(platform.connection.id, ChoiceKind.ALBUM, setOf("album"))

    @Test
    fun `each connection syncs into its own folder with its own records`() {
        chooseAll(home)
        chooseAll(work)

        sync(home)
        sync(work)

        assertTrue(File(folderOf(home), homeSong.path).exists())
        assertTrue(File(folderOf(work), workSong.path).exists())
        val records = database.recordDao()
        assertEquals(homeSong.path, runBlocking { records.get(home.connection.id, "t1") }?.path)
        assertEquals(workSong.path, runBlocking { records.get(work.connection.id, "t1") }?.path)
    }

    @Test
    fun `choices belong to one connection`() {
        chooseAll(home)

        sync(home)
        sync(work)

        assertTrue(File(folderOf(home), homeSong.path).exists())
        assertFalse(File(folderOf(work), workSong.path).exists())
        assertTrue(SyncStates.of(work.connection.id).value.nothingChosen)
    }

    @Test
    fun `a run's record pass drops only its own connection's missing files`() {
        chooseAll(home)
        chooseAll(work)
        sync(home)
        sync(work)
        File(folderOf(work), workSong.path).delete()

        sync(home)

        assertNotNull(runBlocking { database.recordDao().get(work.connection.id, "t1") })
    }

    @Test
    fun `signing one connection out clears its catalogue, not the other's`() {
        chooseAll(home)
        chooseAll(work)
        sync(home)
        sync(work)
        val catalogue = Catalogue(database.catalogueDao())

        runBlocking {
            ConnectionSignOut(
                home.connection.id,
                catalogue,
                clearSignIn = { home.signedIn = false },
                turnOffSchedule = {},
                signedIn = { home.signedIn }
            ).run(flowOf(SyncState())) {}
        }

        assertTrue(runBlocking { catalogue.isEmpty(home.connection.id) })
        assertFalse(runBlocking { catalogue.isEmpty(work.connection.id) })
        // Files, records and choices stay, so signing in again downloads nothing.
        assertTrue(File(folderOf(home), homeSong.path).exists())
        assertNotNull(runBlocking { database.recordDao().get(home.connection.id, "t1") })
        assertEquals(setOf("album"), choices.chosen(home.connection.id, ChoiceKind.ALBUM))
    }
}
