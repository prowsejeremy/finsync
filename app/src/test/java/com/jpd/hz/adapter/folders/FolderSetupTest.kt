package com.jpd.hz.adapter.folders

import android.content.Context
import android.os.Environment
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.db.SyncDatabase
import com.jpd.hz.adapter.db.SyncedFile
import com.jpd.hz.library.LibraryFolderStore
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

private const val PREFS = "settings"
private const val PENDING_MOVE = "library_move"
private const val PLATFORM = "jellyfin"
private const val SERVER_ID = "server-1"
private const val OTHER_SERVER_ID = "server-2"
private const val ITEM = "item"
private const val TRACK = "Music/Darci/Escape Cycle/01 High Speeds.m4a"
private const val ART = "Music/Darci/Escape Cycle/folder.jpg"
private const val BOOK = "Audiobooks/JMC/Hurry/Hurry.m4b"
private const val DEFAULT_LIBRARY = "Media/hz"

/**
 * Changing the Library folder with real folders in a temporary directory and an in-memory sync
 * database: plan, keep, move or refuse, and finding a folder moved by hand from its connection's
 * own records (adapter harness spec, "Running connections"). The pre-T3 first settle went with
 * spec H5; settle now finishes a change cut short, or saves the default on a fresh install.
 */
@RunWith(RobolectricTestRunner::class)
class FolderSetupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var database: SyncDatabase
    private val kurageId = Connection.idOf(PLATFORM, SERVER_ID)
    private val otherId = Connection.idOf(PLATFORM, OTHER_SERVER_ID)
    private val kurage = AdapterFolder(PLATFORM, SERVER_ID, "kurage")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs().edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun setup(signedIn: Set<String> = setOf(kurageId)) =
        FolderSetup(context, database) { signedIn }

    private fun signedOutSetup() = setup(signedIn = emptySet())

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun write(folder: File, vararg paths: String) {
        paths.forEach { path ->
            File(folder, path).apply {
                parentFile?.mkdirs()
                writeText(path)
            }
        }
    }

    /** A synced install: [library] holds kurage's folder, with one record relative to it. */
    private fun settledIn(library: File, vararg extra: String) {
        val folder = File(library, kurage.path)
        write(folder, TRACK, ART, *extra)
        record(kurageId, TRACK, File(folder, TRACK).length())
        AdapterFolderStore(context).save(kurage)
        LibraryFolderStore(context).save(library.path)
    }

    private fun record(connectionId: String, path: String, size: Long) = runBlocking {
        database.recordDao().upsert(SyncedFile(connectionId, ITEM, path, "v1", size))
    }

    private fun trackPath() = runBlocking { database.recordDao().get(kurageId, ITEM)?.path }
    private fun adapterFolder() = AdapterFolderStore(context).pathFor(PLATFORM, SERVER_ID)
    private fun savedLibrary() = LibraryFolderStore(context).saved()

    private fun moved(target: File) = PlannedFolder(kurage, target.path, rename = true)

    @Test
    fun `a fresh install saves the public default as the Library folder`() {
        val publicDefault = File(Environment.getExternalStorageDirectory(), DEFAULT_LIBRARY)

        assertTrue(runBlocking { setup().settle() })

        assertEquals(publicDefault.path, savedLibrary())
        assertTrue(publicDefault.isDirectory)
    }

    @Test
    fun `a saved Library folder is never settled again`() {
        val library = temp.newFolder("hz")
        settledIn(library)

        assertTrue(runBlocking { setup().settle() })

        assertEquals(library.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(library, "kurage/$TRACK").isFile)
    }

    @Test
    fun `a change cut short is finished by the next settle`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library, BOOK)
        val other = temp.newFolder("Music")
        val from = File(library, "kurage")
        val to = File(other, "kurage")
        // As if hz stopped after renaming Music: the change was saved, nothing else was.
        to.mkdirs()
        assertTrue(File(from, "Music").renameTo(File(to, "Music")))
        val move = PendingMove(
            other.path,
            listOf(kurage),
            listOf("Music", "Audiobooks", "Playlists").map {
                File(from, it).path to File(to, it).path
            }
        )
        prefs().edit().putString(PENDING_MOVE, FolderMoves.encode(move)).commit()

        assertTrue(runBlocking { setup().settle() })

        assertEquals(other.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(to, TRACK).isFile)
        assertTrue(File(to, BOOK).isFile)
        assertEquals(TRACK, trackPath())
        assertNull(prefs().getString(PENDING_MOVE, null))
    }

    @Test
    fun `a change waits for an earlier one that can't be finished`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        // The earlier change's target is taken, so its rename can never land.
        val blocked = File(temp.root, "taken")
        write(blocked, "Music/mine.mp3")
        val move = PendingMove(
            blocked.parent,
            listOf(kurage),
            listOf(File(library, "kurage/Music").path to File(blocked, "Music").path)
        )
        prefs().edit().putString(PENDING_MOVE, FolderMoves.encode(move)).commit()

        val result = runBlocking { setup().changeLibrary(other.path) }

        assertEquals(LibraryChange.MoveUnfinished, result)
        assertEquals(library.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(library, "kurage/$TRACK").isFile)
    }

    @Test
    fun `the same folder again is Same, and changes nothing`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)

        assertEquals(LibraryChangePlan.Same, runBlocking { setup().plan(library.path) })
        assertEquals(LibraryChange.Unchanged, runBlocking { setup().changeLibrary(library.path) })
    }

    @Test
    fun `a folder holding the Library keeps the connection's folder where it is`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val wider = File(temp.root, "Media")

        val result = runBlocking { setup().changeLibrary(wider.path) }

        assertEquals(LibraryChange.Changed(emptyList()), result)
        assertEquals(wider.path, savedLibrary())
        assertEquals("hz/kurage", adapterFolder())
        assertTrue(File(library, "kurage/$TRACK").isFile)
        assertEquals(TRACK, trackPath())
    }

    @Test
    fun `a folder inside the connection's is refused`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val inside = File(library, "kurage/Music").path

        assertEquals(LibraryChangePlan.Refused, runBlocking { setup().plan(inside) })
        assertEquals(LibraryChange.Refused, runBlocking { setup().changeLibrary(inside) })
        assertEquals(library.path, savedLibrary())
    }

    @Test
    fun `a folder elsewhere gets the connection's folder moved into it`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")

        val plan = runBlocking { setup().plan(other.path) } as LibraryChangePlan.Ready
        val result = runBlocking { setup().changeLibrary(other.path) }

        val target = File(other, "kurage")
        assertTrue(plan.movesFiles)
        assertEquals(other.path, plan.newLibrary)
        assertEquals(LibraryChange.Changed(listOf(moved(target))), result)
        assertEquals(other.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(target, TRACK).isFile)
        assertFalse(File(library, "kurage").exists())
        // Relative to the connection's folder, so the move changed no record.
        assertEquals(TRACK, trackPath())
        assertNull(prefs().getString(PENDING_MOVE, null))
    }

    @Test
    fun `a failed rename changes nothing`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        // As without all-files access: nothing in the Library folder can be renamed.
        assertTrue(library.setWritable(false))
        val result = try {
            runBlocking { setup().changeLibrary(other.path) }
        } finally {
            library.setWritable(true)
        }

        assertEquals(LibraryChange.MoveFailed, result)
        assertEquals(library.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(library, "kurage/$TRACK").isFile)
        assertFalse(File(other, "kurage").exists())
        assertNull(prefs().getString(PENDING_MOVE, null))
    }

    @Test
    fun `a Library folder moved by hand is followed without moving anything`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val moved = File(temp.root, "Documents/Media/hz")
        moved.parentFile!!.mkdirs()
        assertTrue(library.renameTo(moved))

        val plan = runBlocking { setup().plan(moved.path) } as LibraryChangePlan.Ready
        val result = runBlocking { setup().changeLibrary(moved.path) }

        assertFalse(plan.movesFiles)
        assertNull(plan.found)
        assertEquals(LibraryChange.Changed(emptyList()), result)
        assertEquals(moved.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
        assertTrue(File(moved, "kurage/$TRACK").isFile)
    }

    @Test
    fun `a connection's folder moved by hand is found by its files, for the user to confirm`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        File(other, "Mine/Sync").mkdirs()
        assertTrue(File(library, "kurage").renameTo(File(other, "Mine/Sync/kurage2")))

        val plan = runBlocking { setup().plan(other.path) } as LibraryChangePlan.Ready
        val result = runBlocking { setup().changeLibrary(other.path) }

        assertEquals(File(other, "Mine/Sync/kurage2").path, plan.found?.target)
        assertFalse(plan.movesFiles)
        assertEquals(LibraryChange.Changed(emptyList()), result)
        assertEquals("Mine/Sync/kurage2", adapterFolder())
    }

    @Test
    fun `a signed-out connection's folder moved by hand is found by its records`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        File(other, "Mine/Sync").mkdirs()
        assertTrue(File(library, "kurage").renameTo(File(other, "Mine/Sync/kurage2")))

        val plan = runBlocking { signedOutSetup().plan(other.path) } as LibraryChangePlan.Ready
        val result = runBlocking { signedOutSetup().changeLibrary(other.path) }

        assertEquals(File(other, "Mine/Sync/kurage2").path, plan.found?.target)
        assertEquals(LibraryChange.Changed(emptyList()), result)
        assertEquals("Mine/Sync/kurage2", adapterFolder())
    }

    @Test
    fun `signed out beside another saved folder, a folder moved by hand is still found`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        AdapterFolderStore(context).save(AdapterFolder(PLATFORM, OTHER_SERVER_ID, "elsewhere"))
        val other = temp.newFolder("Music")
        assertTrue(File(library, "kurage").renameTo(File(other, "kurage")))

        val plan = runBlocking { signedOutSetup().plan(other.path) } as LibraryChangePlan.Ready

        val found = plan.folders.single { it.folder.serverId == SERVER_ID }
        assertEquals(File(other, "kurage").path, found.target)
        assertFalse(plan.movesFiles)
    }

    @Test
    fun `each folder is looked for by its own connection's records`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val home = AdapterFolder(PLATFORM, OTHER_SERVER_ID, "home")
        AdapterFolderStore(context).save(home)
        record(otherId, "Music/Someone/Else/x.m4a", 1L)
        val other = temp.newFolder("Music")
        File(other, "Mine").mkdirs()
        assertTrue(File(library, "kurage").renameTo(File(other, "Mine/kurage")))

        val plan = runBlocking { setup().plan(other.path) } as LibraryChangePlan.Ready

        val homePlan = plan.folders.single { it.folder == home }
        assertEquals(File(other, "Mine/kurage").path, plan.found?.target)
        assertEquals(kurage, plan.found?.folder)
        // home's records match nothing here, and it's signed out, so it stays as saved.
        assertEquals(PlannedFolder(home, File(library, "home").path, rename = false), homePlan)
    }

    @Test
    fun `a folder of the user's own music is never taken for the connection's`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        // The user's own copy of the same album, among more of their own music.
        assertTrue(File(library, "kurage").renameTo(File(other, "Mine")))
        write(other, "Mine/Music/Other/x.mp3", "Mine/Music/Other/y.mp3")

        assertEquals(
            LibraryChangePlan.NotFound(kurage),
            runBlocking { setup().plan(other.path) }
        )
    }

    @Test
    fun `the search looks below a folder that only looks like the connection's`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        write(other, "Old/Audiobooks/stray.txt")
        assertTrue(File(library, "kurage").renameTo(File(other, "Old/kurage")))

        val plan = runBlocking { setup().plan(other.path) } as LibraryChangePlan.Ready

        assertEquals(File(other, "Old/kurage").path, plan.found?.target)
    }

    @Test
    fun `an empty folder in the way gets the suffix rather than stopping the move`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        val other = temp.newFolder("Music")
        File(other, "kurage").mkdirs()

        val result = runBlocking { setup().changeLibrary(other.path) }

        assertEquals(
            LibraryChange.Changed(listOf(moved(File(other, "kurage (Jellyfin)")))),
            result
        )
        assertEquals("kurage (Jellyfin)", adapterFolder())
        assertNull(prefs().getString(PENDING_MOVE, null))
    }

    @Test
    fun `another connection's folder that's gone, with nothing synced, is kept as saved`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        AdapterFolderStore(context).save(AdapterFolder(PLATFORM, OTHER_SERVER_ID, "elsewhere"))
        val wider = File(temp.root, "Media")

        val result = runBlocking { setup(setOf(kurageId, otherId)).changeLibrary(wider.path) }

        assertEquals(LibraryChange.Changed(emptyList()), result)
        assertEquals("hz/kurage", adapterFolder())
        assertEquals("elsewhere", AdapterFolderStore(context).pathFor(PLATFORM, OTHER_SERVER_ID))
    }

    @Test
    fun `a folder with no records is never pointed at one that holds files`() {
        // After the rebuild every connection has no records; a folder of that name in the new
        // Library folder may be anyone's, and the connection's cleanup would empty it.
        val library = temp.newFolder("Media", "hz")
        LibraryFolderStore(context).save(library.path)
        AdapterFolderStore(context).save(AdapterFolder(PLATFORM, OTHER_SERVER_ID, "elsewhere"))
        val other = temp.newFolder("Music")
        write(other, "elsewhere/Mine/x.mp3")

        val plan = runBlocking { signedOutSetup().plan(other.path) } as LibraryChangePlan.Ready

        assertEquals(
            listOf(
                PlannedFolder(
                    AdapterFolder(PLATFORM, OTHER_SERVER_ID, "elsewhere"),
                    File(library, "elsewhere").path,
                    rename = false
                )
            ),
            plan.folders
        )
    }

    @Test
    fun `a signed-in connection's folder nowhere to be found changes nothing`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        assertTrue(File(library, "kurage").deleteRecursively())
        val other = temp.newFolder("Music")
        write(other, "Mine/Music/x.mp3")

        assertEquals(LibraryChangePlan.NotFound(kurage), runBlocking { setup().plan(other.path) })
        val change = runBlocking { setup().changeLibrary(other.path) }
        assertEquals(LibraryChange.NotFound(kurage), change)
        assertEquals(library.path, savedLibrary())
        assertEquals("kurage", adapterFolder())
    }

    @Test
    fun `a signed-out connection's folder nowhere to be found stays as saved`() {
        val library = temp.newFolder("Media", "hz")
        settledIn(library)
        assertTrue(File(library, "kurage").deleteRecursively())
        val other = temp.newFolder("Music")

        val plan = runBlocking { signedOutSetup().plan(other.path) } as LibraryChangePlan.Ready

        assertEquals(
            listOf(PlannedFolder(kurage, File(library, "kurage").path, rename = false)),
            plan.folders
        )
    }

    @Test
    fun `changing while a sync runs changes nothing`() {
        val library = temp.newFolder("hz")
        settledIn(library)
        val other = temp.newFolder("other")

        val result = runBlocking {
            FolderSetup.lock.lock()
            try {
                setup().changeLibrary(other.path)
            } finally {
                FolderSetup.lock.unlock()
            }
        }

        assertEquals(LibraryChange.Busy, result)
        assertEquals(library.path, savedLibrary())
    }

    @Test
    fun `a folder that can't be listed is unreadable`() {
        val missing = File(temp.root, "missing").path

        assertEquals(LibraryChangePlan.Unreadable, runBlocking { setup().plan(missing) })
        assertEquals(LibraryChange.Unreadable, runBlocking { setup().changeLibrary(missing) })
    }
}
