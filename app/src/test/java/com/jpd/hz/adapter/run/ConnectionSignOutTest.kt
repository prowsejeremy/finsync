package com.jpd.hz.adapter.run

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.SourceItem
import com.jpd.hz.adapter.choices.ChoiceStore
import com.jpd.hz.adapter.db.SyncDatabase
import com.jpd.hz.adapter.db.SyncedFile
import com.jpd.hz.adapter.folders.AdapterFolder
import com.jpd.hz.adapter.folders.AdapterFolderStore
import com.jpd.hz.adapter.folders.FolderSetup
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val PLATFORM = "jellyfin"
private const val SERVER_ID = "server-1"
private const val KURAGE = "$PLATFORM:$SERVER_ID"
private const val HOME = "plex:77b0"
private const val WAIT_MS = 2_000L
private const val STEP_MS = 5L

/**
 * Signs a connection out against an in-memory sync database and the real settings prefs: the
 * order of its steps, the lock, and what it keeps (adapter harness spec, "Running connections";
 * T4 B1, B2).
 */
@RunWith(RobolectricTestRunner::class)
class ConnectionSignOutTest {

    private lateinit var context: Context
    private lateinit var database: SyncDatabase
    private lateinit var catalogue: Catalogue
    private lateinit var signOut: ConnectionSignOut
    // The edges unit tests can't reach: the platform's sign-in store and WorkManager.
    private var signedIn = true
    private var scheduled = true

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        catalogue = Catalogue(database.catalogueDao())
        signOut = ConnectionSignOut(
            KURAGE, catalogue, { signedIn = false }, { scheduled = false }, { signedIn }
        )
    }

    @After
    fun tearDown() {
        database.close()
        // Tests hold the lock as a sync would. Every test shares it, so one that fails mustn't
        // leave it held for the rest.
        if (FolderSetup.lock.isLocked) FolderSetup.lock.unlock()
    }

    private fun prefs() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun oneAlbum() = SourceCatalogue(
        items = listOf(
            SourceItem("t1", "Music/x.m4a", "v1", ItemKind.MUSIC, "Track", null, null, null)
        ),
        groups = listOf(ChoiceGroup("a1", ChoiceKind.ALBUM, "Album", "Artist", listOf("t1")))
    )

    // A signed-in connection that has synced: a catalogue, records, choices and a folder.
    private fun synced() = runBlocking {
        catalogue.write(KURAGE, oneAlbum())
        database.recordDao().upsert(SyncedFile(KURAGE, "t1", "Music/x.m4a", "v1", 10L))
        ChoiceStore(context).setChosen(KURAGE, ChoiceKind.ALBUM, setOf("a1"))
        ChoiceStore(context).setChosen(KURAGE, ChoiceKind.PLAYLIST, setOf("p1"))
        ChoiceStore(context).setChosen(KURAGE, ChoiceKind.BOOK, setOf("b1"))
        AdapterFolderStore(context).save(AdapterFolder(PLATFORM, SERVER_ID, "kurage"))
    }

    // Lets sign-out run until [condition] holds, or for at most WAIT_MS.
    private suspend fun waitFor(condition: () -> Boolean) {
        withTimeoutOrNull(WAIT_MS) {
            while (!condition()) delay(STEP_MS)
        }
    }

    @Test
    fun `signing out clears the sign-in, catalogue and schedule, and keeps the rest`() =
        runBlocking {
            synced()

            signOut.run(MutableStateFlow(SyncState())) { error("no sync is running") }

            assertFalse(signedIn)
            assertFalse(scheduled)
            assertTrue(catalogue.isEmpty(KURAGE))
            // The records: signing in again to the same server downloads and re-tags nothing.
            assertEquals(1, database.recordDao().count(KURAGE))
            assertEquals("Music/x.m4a", database.recordDao().get(KURAGE, "t1")?.path)
            // The choices: signing in again finds them as they were.
            val choices = ChoiceStore(context)
            assertEquals(setOf("a1"), choices.chosen(KURAGE, ChoiceKind.ALBUM))
            assertEquals(setOf("p1"), choices.chosen(KURAGE, ChoiceKind.PLAYLIST))
            assertEquals(setOf("b1"), choices.chosen(KURAGE, ChoiceKind.BOOK))
            assertEquals("kurage", AdapterFolderStore(context).pathFor(PLATFORM, SERVER_ID))
            assertFalse(FolderSetup.lock.isLocked)
        }

    @Test
    fun `signing out leaves another connection's catalogue and records alone`() = runBlocking {
        synced()
        catalogue.write(HOME, oneAlbum())
        database.recordDao().upsert(SyncedFile(HOME, "t1", "Music/x.m4a", "v1", 10L))

        signOut.run(MutableStateFlow(SyncState())) { error("no sync is running") }

        assertTrue(catalogue.isEmpty(KURAGE))
        assertEquals(oneAlbum().groups, catalogue.stored(HOME).groups)
        assertEquals(1, database.recordDao().count(HOME))
    }

    @Test
    fun `the sign-in goes at once and the catalogue once a running sync lets go`() = runBlocking {
        synced()
        // The test holds the lock, as a sync does for its whole run.
        val states = MutableStateFlow(SyncState(isRunning = true))
        var stops = 0
        FolderSetup.lock.lock()
        val job = launch {
            signOut.run(states) {
                stops++
                states.value = SyncState(wasStopped = true)
            }
        }
        waitFor { stops > 0 }
        val signedInWhileWaiting = signedIn
        val scheduledWhileWaiting = scheduled
        val emptyWhileWaiting = catalogue.isEmpty(KURAGE)
        FolderSetup.lock.unlock()
        job.join()

        assertEquals(1, stops)
        assertFalse(signedInWhileWaiting)
        assertFalse(scheduledWhileWaiting)
        assertFalse(emptyWhileWaiting)
        assertTrue(catalogue.isEmpty(KURAGE))
        assertFalse(FolderSetup.lock.isLocked)
    }

    @Test
    fun `a sync that says it's running while sign-out waits is stopped too`() = runBlocking {
        synced()
        val states = MutableStateFlow(SyncState())
        var stops = 0
        FolderSetup.lock.lock()
        val job = launch { signOut.run(states) { stops++ } }
        waitFor { !signedIn }
        // Sign-out now waits for the lock; the sync it raced says it's running only now.
        states.value = SyncState(isRunning = true, currentTrack = "Fetching library...")
        waitFor { stops > 0 }
        FolderSetup.lock.unlock()
        job.join()

        assertEquals(1, stops)
        assertTrue(catalogue.isEmpty(KURAGE))
    }

    @Test
    fun `a sign-in made while sign-out waits keeps its catalogue`() = runBlocking {
        synced()
        FolderSetup.lock.lock()
        val job = launch { signOut.run(MutableStateFlow(SyncState())) {} }
        waitFor { !signedIn }
        // Signed in again; its refresh has written the catalogue (here, the one already there).
        signedIn = true
        FolderSetup.lock.unlock()
        job.join()

        assertFalse(catalogue.isEmpty(KURAGE))
        assertFalse(FolderSetup.lock.isLocked)
    }

    @Test
    fun `a stop Android refuses doesn't stop the sign-out`() = runBlocking {
        synced()
        FolderSetup.lock.lock()
        val job = launch {
            signOut.run(MutableStateFlow(SyncState(isRunning = true))) {
                throw IllegalStateException("Not allowed to start service")
            }
        }
        waitFor { !signedIn }
        FolderSetup.lock.unlock()
        job.join()

        assertTrue(catalogue.isEmpty(KURAGE))
        assertFalse(FolderSetup.lock.isLocked)
    }
}
