package com.jpd.hz.sync

import android.content.Context
import android.os.Environment
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.adapter.AdapterFolder
import com.jpd.hz.adapter.AdapterFolderStore
import com.jpd.hz.adapter.FolderMoves
import com.jpd.hz.adapter.PendingMove
import com.jpd.hz.db.CatalogueTrack
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.db.SyncedAlbum
import com.jpd.hz.db.SyncedTrack
import com.jpd.hz.library.LibraryFolderStore
import com.jpd.hz.model.ServerConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private const val SERVER_ID = "server-1"
private const val TRACK = "Music/Darci/Escape Cycle/01 High Speeds.m4a"
private const val ART = "Music/Darci/Escape Cycle/folder.jpg"
private const val BOOK = "Audiobooks/JMC/Hurry/Hurry.m4b"

/** Moves real folders in a temporary directory, with an in-memory sync database. */
@RunWith(RobolectricTestRunner::class)
class FolderSetupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var database: SyncDatabase
    private lateinit var setup: FolderSetup
    private val config = ServerConfig(
        serverUrl = "http://kurage", serverId = SERVER_ID, serverName = "kurage",
        userId = "user", username = "me", accessToken = "token"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        setup = FolderSetup(context, database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun prefs() = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private fun write(folder: File, vararg paths: String) {
        paths.forEach { path ->
            File(folder, path).apply {
                parentFile?.mkdirs()
                writeText(path)
            }
        }
    }

    /** A pre-T3 install: files in [folder], records with full paths, the folder in prefs. */
    private fun syncedBeforeT3(folder: File, vararg extra: String) {
        write(folder, TRACK, ART, *extra)
        runBlocking {
            val sizes = File(folder, TRACK).length()
            database.syncDao().upsertTrack(
                SyncedTrack("item", File(folder, TRACK).path, "/server/a.m4a", "album", sizes)
            )
            database.syncDao().upsertAlbum(
                SyncedAlbum("album", "Escape Cycle", "Darci", 14, File(folder, ART).path)
            )
        }
        AdapterFolderStore(context).save(AdapterFolder("jellyfin", SERVER_ID, folder.path))
    }

    /** A settled install: [library] holds Jellyfin's folder [name], with relative records. */
    private fun settledIn(library: File, name: String = "kurage") {
        val folder = File(library, name)
        write(folder, TRACK, ART)
        runBlocking {
            database.syncDao().upsertTrack(
                SyncedTrack("item", TRACK, "/server/a.m4a", "album", File(folder, TRACK).length())
            )
            database.catalogueDao().insertTracks(listOf(catalogueTrack("item")))
        }
        AdapterFolderStore(context).save(AdapterFolder("jellyfin", SERVER_ID, name))
        LibraryFolderStore(context).save(library.path)
    }

    private fun catalogueTrack(itemId: String) = CatalogueTrack(
        itemId, "album", "High Speeds", listOf("Darci"), emptyList(), "Darci", null, 1, null,
        null, null, null, null, null
    )

    private fun trackPath() = runBlocking { database.syncDao().getTrack("item")?.localPath }
    private fun artworkPath() = runBlocking { database.syncDao().getAlbum("album")?.artworkPath }
    private fun adapterFolder() = AdapterFolderStore(context).pathFor("jellyfin", SERVER_ID)
    private fun savedLibrary() = LibraryFolderStore(context).saved()

    @Test
    fun aPickedFolderBecomesTheLibraryAndItsContentMovesIntoTheServersFolder() {
        val picked = temp.newFolder("Media", "hz")
        syncedBeforeT3(picked, BOOK, "Breezie.m3u8")
        File(picked, "kurage").mkdirs()
        prefs().edit().putString("sync_directory", picked.path).commit()

        assertTrue(runBlocking { setup.settle(config) })

        val target = File(picked, "kurage")
        assertEquals(picked.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(target, TRACK).isFile)
        assertTrue(File(target, BOOK).isFile)
        assertFalse(File(picked, "Music").exists())
        // Not the adapter's: it stays where it was, now one of the user's own files.
        assertTrue(File(picked, "Breezie.m3u8").isFile)
        assertEquals(TRACK, trackPath())
        assertEquals(ART, artworkPath())
        assertNull(prefs().getString("sync_directory", null))
        assertNull(prefs().getString("library_move", null))
    }

    @Test
    fun aServerFolderThatHoldsFilesGetsTheSuffix() {
        val picked = temp.newFolder("tmp")
        syncedBeforeT3(picked)
        write(picked, "kurage/mine.txt")

        assertTrue(runBlocking { setup.settle(config) })

        assertEquals("kurage (Jellyfin)", adapterFolder())
        assertTrue(File(picked, "kurage (Jellyfin)/$TRACK").isFile)
        assertTrue(File(picked, "kurage/mine.txt").isFile)
    }

    @Test
    fun aFolderInTheDefaultLibraryKeepsItsParentAndOnlyItsRecordsChange() {
        val library = File(Environment.getExternalStorageDirectory(), "Media/hz")
        val folder = File(library, "kurage")
        syncedBeforeT3(folder)

        assertTrue(runBlocking { setup.settle(config) })

        assertEquals(library.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(folder, TRACK).isFile)
        assertEquals(TRACK, trackPath())
    }

    @Test
    fun recordsOutsideTheFolderAreDroppedWhenTheyBecomeRelative() {
        val picked = temp.newFolder("hz")
        syncedBeforeT3(picked)
        runBlocking {
            database.syncDao().upsertTrack(
                SyncedTrack("elsewhere", "/storage/other/x.m4a", null, null, 1L)
            )
        }

        assertTrue(runBlocking { setup.settle(config) })

        assertNull(runBlocking { database.syncDao().getTrack("elsewhere") })
        assertEquals(TRACK, trackPath())
    }

    @Test
    fun aFailedMoveSavesNothingSoTheNextLaunchTriesAgain() {
        val picked = temp.newFolder("hz")
        syncedBeforeT3(picked, BOOK)
        // As without all-files access: nothing in the folder can be renamed.
        assertTrue(picked.setWritable(false))
        val settled = try {
            runBlocking { setup.settle(config) }
        } finally {
            picked.setWritable(true)
        }

        assertFalse(settled)
        assertNull(prefs().getString("library_move", null))
        assertNull(savedLibrary())
        assertEquals(picked.path, adapterFolder())
        assertTrue(File(picked, TRACK).isFile)
        assertEquals(File(picked, TRACK).path, trackPath())
    }

    @Test
    fun aMoveCutShortIsFinishedByTheNextSettle() {
        val picked = temp.newFolder("hz")
        syncedBeforeT3(picked, BOOK)
        val target = File(picked, "kurage")
        // As if hz stopped after renaming Music: the move was saved, nothing else was.
        target.mkdirs()
        assertTrue(File(picked, "Music").renameTo(File(target, "Music")))
        val move = PendingMove(
            picked.path,
            listOf(AdapterFolder("jellyfin", SERVER_ID, "kurage")),
            listOf("Music", "Audiobooks", "Playlists").map {
                File(picked, it).path to File(target, it).path
            },
            recordsFrom = picked.path
        )
        prefs().edit().putString("library_move", FolderMoves.encode(move)).commit()

        assertTrue(runBlocking { setup.settle(config) })

        assertEquals(picked.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(target, BOOK).isFile)
        assertEquals(TRACK, trackPath())
        assertNull(prefs().getString("library_move", null))
    }

    @Test
    fun signedOutAfterSyncingSavesNothingUntilTheNextSignIn() {
        val picked = temp.newFolder("hz")
        syncedBeforeT3(picked)

        assertTrue(runBlocking { setup.settle(null) })
        assertNull(savedLibrary())
        assertTrue(runBlocking { setup.settle(config) })

        assertEquals(picked.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
    }

    @Test
    fun signedOutWithOnlyRecordsSavesNothing() {
        runBlocking {
            database.syncDao().upsertTrack(SyncedTrack("item", "/old/x.m4a", null, null, 1L))
        }

        assertTrue(runBlocking { setup.settle(null) })

        assertNull(savedLibrary())
    }

    @Test
    fun aSavedLibraryIsNeverSettledAgain() {
        val library = temp.newFolder("hz")
        settledIn(library)

        assertTrue(runBlocking { setup.settle(config) })

        assertEquals("kurage", adapterFolder())
        assertTrue(File(library, "kurage/$TRACK").isFile)
    }

    @Test
    fun aFolderHoldingTheLibraryKeepsJellyfinsFolderWhereItIs() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val wider = File(temp.root, "Media")

        val result = runBlocking { setup.changeLibrary(wider.path, SERVER_ID) }

        assertEquals(LibraryChange.Changed(null), result)
        assertEquals(wider.path, savedLibrary())
        assertEquals("hz/kurage", adapterFolder())
        assertTrue(File(library, "kurage/$TRACK").isFile)
        assertEquals(TRACK, trackPath())
    }

    @Test
    fun aFolderInsideJellyfinsIsRefused() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)

        val inside = File(library, "kurage/Music").path

        val result = runBlocking { setup.changeLibrary(inside, SERVER_ID) }

        assertEquals(LibraryChange.Refused, result)
        assertEquals(library.path, savedLibrary())
    }

    @Test
    fun aFolderElsewhereGetsJellyfinsFolderMovedIntoIt() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")

        val plan = runBlocking { setup.plan(other.path, SERVER_ID) } as LibraryChangePlan.Ready
        val result = runBlocking { setup.changeLibrary(other.path, SERVER_ID) }

        val moved = File(other, "kurage")
        assertTrue(plan.movesFiles)
        assertEquals(LibraryChange.Changed(moved.path), result)
        assertEquals(other.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(moved, TRACK).isFile)
        assertFalse(File(library, "kurage").exists())
        // Relative to Jellyfin's folder, so the move changed no record.
        assertEquals(TRACK, trackPath())
    }

    @Test
    fun aLibraryMovedByHandIsFollowedWithoutMovingAnything() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val moved = File(temp.root, "Documents/Media/hz")
        moved.parentFile!!.mkdirs()
        assertTrue(library.renameTo(moved))

        val plan = runBlocking { setup.plan(moved.path, SERVER_ID) } as LibraryChangePlan.Ready
        val result = runBlocking { setup.changeLibrary(moved.path, SERVER_ID) }

        assertFalse(plan.movesFiles)
        assertEquals(LibraryChange.Changed(null), result)
        assertEquals(moved.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(moved, "kurage/$TRACK").isFile)
    }

    @Test
    fun jellyfinsFolderMovedByHandIsFoundByItsFilesForTheUserToConfirm() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        File(other, "Mine/Sync").mkdirs()
        assertTrue(File(library, "kurage").renameTo(File(other, "Mine/Sync/kurage2")))

        val plan = runBlocking { setup.plan(other.path, SERVER_ID) } as LibraryChangePlan.Ready
        val result = runBlocking { setup.changeLibrary(other.path, SERVER_ID) }

        assertEquals(File(other, "Mine/Sync/kurage2").path, plan.found?.target)
        assertEquals(LibraryChange.Changed(null), result)
        assertEquals("Mine/Sync/kurage2", adapterFolder())
    }

    @Test
    fun aFolderOfTheUsersOwnMusicIsNeverTakenForJellyfins() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        // The user's own copy of the same album, among more of their own music.
        assertTrue(File(library, "kurage").renameTo(File(other, "Mine")))
        write(other, "Mine/Music/Other/x.mp3", "Mine/Music/Other/y.mp3")

        assertEquals(LibraryChangePlan.NotFound, runBlocking { setup.plan(other.path, SERVER_ID) })
    }

    @Test
    fun theSearchLooksBelowAFolderThatOnlyLooksLikeJellyfins() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        write(other, "Old/Audiobooks/stray.txt")
        assertTrue(File(library, "kurage").renameTo(File(other, "Old/kurage")))

        val plan = runBlocking { setup.plan(other.path, SERVER_ID) } as LibraryChangePlan.Ready

        assertEquals(File(other, "Old/kurage").path, plan.found?.target)
    }

    @Test
    fun anEmptyFolderInTheWayGetsTheSuffixRatherThanStoppingTheMove() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        File(other, "kurage").mkdirs()

        val result = runBlocking { setup.changeLibrary(other.path, SERVER_ID) }

        assertEquals(LibraryChange.Changed(File(other, "kurage (Jellyfin)").path), result)
        assertEquals("kurage (Jellyfin)", adapterFolder())
        assertNull(prefs().getString("library_move", null))
    }

    @Test
    fun anotherServersFolderThatsGoneIsKeptAsSaved() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        AdapterFolderStore(context).save(AdapterFolder("jellyfin", "server-2", "elsewhere"))
        val wider = File(temp.root, "Media")

        val result = runBlocking { setup.changeLibrary(wider.path, SERVER_ID) }

        assertEquals(LibraryChange.Changed(null), result)
        assertEquals("hz/kurage", adapterFolder())
    }

    @Test
    fun aChangeWaitsForTheFirstSettle() {
        val picked = temp.newFolder("hz")
        syncedBeforeT3(picked)
        val other = temp.newFolder("other")

        val result = runBlocking { setup.changeLibrary(other.path, SERVER_ID) }

        assertEquals(LibraryChange.MoveUnfinished, result)
        assertNull(savedLibrary())
        assertEquals(File(picked, TRACK).path, trackPath())
    }

    @Test
    fun jellyfinsFolderNowhereToBeFoundChangesNothing() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        assertTrue(File(library, "kurage").deleteRecursively())
        val other = temp.newFolder("Music")
        write(other, "Mine/Music/x.mp3")

        assertEquals(LibraryChangePlan.NotFound, runBlocking { setup.plan(other.path, SERVER_ID) })
        val change = runBlocking { setup.changeLibrary(other.path, SERVER_ID) }
        assertEquals(LibraryChange.NotFound, change)
        assertEquals(library.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
    }

    @Test
    fun changingWhileASyncRunsChangesNothing() {
        val library = temp.newFolder("hz")
        settledIn(library)
        val other = temp.newFolder("other")

        val result = runBlocking {
            FolderSetup.lock.lock()
            try {
                setup.changeLibrary(other.path, SERVER_ID)
            } finally {
                FolderSetup.lock.unlock()
            }
        }

        assertEquals(LibraryChange.Busy, result)
        assertEquals(library.path, savedLibrary())
    }

    @Test
    fun aFolderThatCantBeListedIsUnreadable() {
        val missing = File(temp.root, "missing").path
        assertEquals(LibraryChangePlan.Unreadable, runBlocking { setup.plan(missing, SERVER_ID) })
    }
}
