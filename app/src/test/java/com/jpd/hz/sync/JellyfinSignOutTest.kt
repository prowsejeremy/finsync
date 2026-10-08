package com.jpd.hz.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.adapter.AdapterFolder
import com.jpd.hz.adapter.AdapterFolderStore
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.db.SyncedAlbum
import com.jpd.hz.db.SyncedTrack
import com.jpd.hz.library.SyncSelections
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.SyncState
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

private const val SERVER_ID = "server-1"
private const val WAIT_MS = 2_000L
private const val STEP_MS = 5L

/** Signs out against an in-memory sync database and the real settings prefs. */
@RunWith(RobolectricTestRunner::class)
class JellyfinSignOutTest {

    private lateinit var context: Context
    private lateinit var database: SyncDatabase
    private lateinit var catalogue: JellyfinCatalogue
    private lateinit var signOut: JellyfinSignOut
    // The edges unit tests can't reach: the encrypted sign-in store and WorkManager.
    private var signedIn = true
    private var scheduled = true

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        catalogue = JellyfinCatalogue(context, database.catalogueDao())
        signOut = JellyfinSignOut(
            context, catalogue, { signedIn = false }, { scheduled = false }, { signedIn }
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

    // A signed-in install that has synced: a catalogue, records, selections and a schedule.
    private fun synced() = runBlocking {
        val track = MediaItem(id = "t1", name = "Track", type = "Audio", albumId = "a1")
        catalogue.write(listOf(track))
        database.syncDao().upsertTrack(SyncedTrack("t1", "Music/x.m4a", "/srv/x.m4a", "a1", 10L))
        database.syncDao().upsertAlbum(SyncedAlbum("a1", "Album", "Artist", 1, "Music/folder.jpg"))
        prefs().edit()
            .putStringSet("selected_albums", setOf("a1"))
            .putString("auto_sync_interval", "6")
            .commit()
        SyncSelections(context).setPlaylistIds(setOf("p1"))
        SyncSelections(context).setBookIds(setOf("b1"))
        AdapterFolderStore(context).save(AdapterFolder("jellyfin", SERVER_ID, "kurage"))
    }

    @Test
    fun `signing out clears the sign-in, catalogue and schedule, and keeps the rest`() =
        runBlocking {
            synced()

            signOut.run(MutableStateFlow(SyncState())) { error("no sync is running") }

            assertFalse(signedIn)
            assertFalse(scheduled)
            assertEquals("disabled", prefs().getString("auto_sync_interval", null))
            assertTrue(catalogue.isEmpty())
            // The records: signing in again to the same server downloads and re-tags nothing.
            assertEquals(1, database.syncDao().trackCount())
            assertEquals("Music/folder.jpg", database.syncDao().getAlbum("a1")?.artworkPath)
            // The selections: emptied, the next sync would take every album and delete every book.
            assertEquals(setOf("a1"), prefs().getStringSet("selected_albums", null))
            assertEquals(setOf("p1"), SyncSelections(context).playlistIds())
            assertEquals(setOf("b1"), SyncSelections(context).bookIds())
            assertEquals("kurage", AdapterFolderStore(context).pathFor("jellyfin", SERVER_ID))
            assertFalse(FolderSetup.lock.isLocked)
        }

    // Lets sign-out run until [condition] holds, or for at most WAIT_MS.
    private suspend fun waitFor(condition: () -> Boolean) {
        withTimeoutOrNull(WAIT_MS) {
            while (!condition()) delay(STEP_MS)
        }
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
        val emptyWhileWaiting = catalogue.isEmpty()
        FolderSetup.lock.unlock()
        job.join()

        assertEquals(1, stops)
        assertFalse(signedInWhileWaiting)
        assertFalse(emptyWhileWaiting)
        assertTrue(catalogue.isEmpty())
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
        assertTrue(catalogue.isEmpty())
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

        assertFalse(catalogue.isEmpty())
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

        assertTrue(catalogue.isEmpty())
        assertFalse(FolderSetup.lock.isLocked)
    }
}
