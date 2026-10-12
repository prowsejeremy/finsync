package com.jpd.hz.adapter.run

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.choices.ALL
import com.jpd.hz.adapter.choices.ChoiceStore
import com.jpd.hz.adapter.db.SyncDatabase
import com.jpd.hz.adapter.files.FakeTagger
import com.jpd.hz.adapter.folders.FolderSetup
import com.jpd.hz.tags.TagFingerprint
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private const val ALBUM = "a1"
private const val OTHER_ALBUM = "a2"
private const val PLAYLIST = "p1"
private const val BOOK = "b1"
// Long enough for a run started on another coroutine to reach the lock.
private const val SETTLE_MS = 50L

/**
 * The generic sync run (adapter harness spec, "The sync run"), against a fake source and a
 * temporary Library folder: what downloads, what's re-linked or re-tagged, what cleanup keeps,
 * and the guards that stop a run before it touches a file.
 */
@RunWith(RobolectricTestRunner::class)
class SyncRunTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: SyncDatabase
    private lateinit var choices: ChoiceStore
    private lateinit var library: File
    private lateinit var folder: File
    private lateinit var source: FakeSource
    private lateinit var platform: FakePlatform
    private var tagger = FakeTagger()

    private val one = song("t1", "Music/Air/Moon Safari/01 La Femme.flac")
    private val two = song("t2", "Music/Air/Moon Safari/02 Sexy Boy.flac")
    private val three = song("t3", "Music/Air/Talkie Walkie/01 Venus.flac")
    private val book = song("b1", "Audiobooks/Ann/Tale/Tale.m4b", kind = ItemKind.BOOK)

    private val id get() = platform.connection.id

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        choices = ChoiceStore(context)
        library = temp.newFolder("library")
        folder = File(library, "fakeserver")
        source = FakeSource(catalogueOf())
        platform = FakePlatform(source)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun catalogueOf(
        failedKinds: Set<ChoiceKind> = emptySet(),
        items: List<com.jpd.hz.adapter.SourceItem> = listOf(one, two, three, book)
    ) = SourceCatalogue(
        items = items,
        groups = listOf(
            group(ALBUM, ChoiceKind.ALBUM, "t1", "t2"),
            group(OTHER_ALBUM, ChoiceKind.ALBUM, "t3"),
            group(PLAYLIST, ChoiceKind.PLAYLIST, "t3", "t1", "t3"),
            group(BOOK, ChoiceKind.BOOK, "b1")
        ),
        failedKinds = failedKinds
    )

    private fun run(settled: Boolean = true) = runBlocking {
        SyncRun(
            connectionId = id,
            database = database,
            choices = choices,
            tagger = tagger,
            platforms = { listOf(platform) },
            folderFor = { _, _ -> folder },
            library = { library },
            settle = { settled }
        ).run()
        SyncStates.of(id).value
    }

    private fun choose(kind: ChoiceKind, vararg ids: String) =
        choices.setChosen(id, kind, ids.toSet())

    private fun record(itemId: String) = runBlocking { database.recordDao().get(id, itemId) }

    private fun fileOf(item: com.jpd.hz.adapter.SourceItem) = File(folder, item.path)

    // A file of the user's own, or one left from before.
    private fun fileAt(parent: File, path: String) =
        File(parent, path).apply { parentFile!!.mkdirs(); writeText("x") }

    @Test
    fun `chosen items download, tagged, and a second run downloads nothing`() {
        choose(ChoiceKind.ALBUM, ALBUM)

        val first = run()

        assertTrue(first.syncComplete)
        assertEquals(0, first.failedItems)
        assertEquals(listOf("t1", "t2"), source.opened)
        assertTrue(fileOf(one).readText().startsWith("bytes of t1"))
        assertEquals(TagFingerprint.of(one.fields!!), record("t1")?.tagFingerprint)
        assertFalse(fileOf(three).exists())

        source.opened.clear()
        run()
        assertTrue(source.opened.isEmpty())
    }

    @Test
    fun `nothing chosen stops the run before it touches a file`() {
        val mine = fileAt(folder, "Music/Air/old.flac")

        val state = run()

        assertTrue(state.nothingChosen)
        assertFalse(state.isRunning)
        assertTrue(mine.exists())
        assertTrue(source.opened.isEmpty())
        assertTrue(runBlocking { Catalogue(database.catalogueDao()).isEmpty(id) })
    }

    @Test
    fun `a connection signed out since the run was asked for does nothing`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        platform.signedIn = false

        val state = run()

        assertFalse(state.isRunning)
        assertNull(state.errorMessage)
        assertTrue(source.opened.isEmpty())
        assertTrue(runBlocking { Catalogue(database.catalogueDao()).isEmpty(id) })
    }

    @Test
    fun `a Library folder that isn't settled stops the run with a message`() {
        choose(ChoiceKind.ALBUM, ALBUM)

        val state = run(settled = false)

        assertNotNull(state.errorMessage)
        assertTrue(source.opened.isEmpty())
    }

    @Test
    fun `a source whose catalogue fails ends the run with its message`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        source.catalogueFails = "Server returned 500"

        assertEquals("Server returned 500", run().errorMessage)
    }

    @Test
    fun `a file already on disk with no record is re-linked and tagged, not downloaded`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        fileOf(one).apply { parentFile!!.mkdirs(); writeText("from before") }

        run()

        assertFalse("t1" in source.opened)
        assertTrue(fileOf(one).readText().startsWith("from before"))
        assertEquals(TagFingerprint.of(one.fields!!), record("t1")?.tagFingerprint)
    }

    @Test
    fun `changed fields re-tag the file in place, and a changed version downloads it again`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        run()
        source.opened.clear()
        val retitled = one.copy(fields = mapOf("TITLE" to "La Femme d'argent"))
        val newer = two.copy(version = "v2")
        source.catalogue = catalogueOf(items = listOf(retitled, newer, three, book))

        run()

        assertEquals(listOf("t2"), source.opened)
        assertEquals(TagFingerprint.of(retitled.fields!!), record("t1")?.tagFingerprint)
        assertEquals("v2", record("t2")?.version)
    }

    @Test
    fun `a file that fails once is tried again, and one that fails twice is counted`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        source.failOpens["t1"] = 1
        source.failOpens["t2"] = 2

        val state = run()

        assertTrue(fileOf(one).exists())
        assertFalse(fileOf(two).exists())
        assertEquals(1, state.failedItems)
        assertFalse(File(fileOf(two).path + ".part").exists())
    }

    @Test
    fun `a failed tag keeps a fresh download untagged and counts it`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        tagger = FakeTagger(succeeds = false)

        val state = run()

        assertEquals(listOf("t1", "t1", "t2", "t2"), source.opened)
        assertEquals("bytes of t1", fileOf(one).readText())
        assertNull(record("t1")?.tagFingerprint)
        assertEquals(2, state.untaggedFiles)
        assertEquals(0, state.failedItems)
    }

    @Test
    fun `cleanup removes what isn't chosen, only inside the connection's folder`() {
        choose(ChoiceKind.ALBUM, ALBUM, OTHER_ALBUM)
        run()
        val outside = fileAt(library, "Mine/keep.flac")
        choose(ChoiceKind.ALBUM, ALBUM)

        run()

        assertTrue(fileOf(one).exists())
        assertFalse(fileOf(three).exists())
        assertFalse(fileOf(three).parentFile!!.exists())
        assertTrue(outside.exists())
        assertNull(record("t3"))
    }

    @Test
    fun `a kind whose list didn't load keeps its recorded files`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        choose(ChoiceKind.BOOK, BOOK)
        run()
        source.catalogue = catalogueOf(
            failedKinds = setOf(ChoiceKind.BOOK),
            items = listOf(one, two, three)
        ).let { it.copy(groups = it.groups.filterNot { group -> group.kind == ChoiceKind.BOOK }) }

        val state = run()

        assertTrue(fileOf(book).exists())
        assertNotNull(record("b1"))
        assertEquals(1, state.failedItems)
    }

    @Test
    fun `a chosen book list that fails keeps the books with no records or stored catalogue`() {
        // The safety review's case: after a sign-out cleared the catalogue, or a rebuilt
        // database lost the records, a book list that fails must still delete nothing.
        choose(ChoiceKind.ALBUM, ALBUM)
        choose(ChoiceKind.BOOK, BOOK)
        run()
        runBlocking {
            Catalogue(database.catalogueDao()).clear(id)
            database.recordDao().deleteByPath(id, book.path)
        }
        val failed = catalogueOf(failedKinds = setOf(ChoiceKind.BOOK), items = listOf(one, two))
        source.catalogue =
            failed.copy(groups = failed.groups.filterNot { it.kind == ChoiceKind.BOOK })

        val state = run()

        assertTrue(fileOf(book).exists())
        assertEquals(1, state.failedItems)
    }

    @Test
    fun `a kind that failed but isn't chosen doesn't stop cleanup`() {
        choose(ChoiceKind.ALBUM, ALBUM, OTHER_ALBUM)
        run()
        choose(ChoiceKind.ALBUM, ALBUM)
        source.catalogue = catalogueOf(failedKinds = setOf(ChoiceKind.BOOK))

        run()

        assertFalse(fileOf(three).exists())
    }

    @Test
    fun `a plan with nothing in it cleans up nothing`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        run()
        // The chosen album has gone from the source.
        source.catalogue = SourceCatalogue(
            items = listOf(three),
            groups = listOf(group(OTHER_ALBUM, ChoiceKind.ALBUM, "t3"))
        )

        run()

        assertTrue(fileOf(one).exists())
        assertTrue(fileOf(two).exists())
    }

    @Test
    fun `extras outside the connection's folder are never written`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        source.extraFiles = listOf("../outside.jpg" to "x")

        run()

        assertFalse(File(library, "outside.jpg").exists())
    }

    @Test
    fun `a folder that holds the Library folder is refused`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        folder = library.parentFile!!

        val state = run()

        assertTrue(state.errorMessage!!.contains("fakeserver"))
        assertTrue(source.opened.isEmpty())
    }

    @Test
    fun `a saved folder that's gone while records name files in it is refused`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        run()
        folder.deleteRecursively()
        source.opened.clear()

        val state = run()

        assertNotNull(state.errorMessage)
        assertTrue(source.opened.isEmpty())
        assertFalse(folder.exists())
    }

    @Test
    fun `unsafe and repeated paths are never written and count as failed`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        val escaping = one.copy(path = "../escape.flac")
        // Shared storage ignores case, so this is the same file as two's.
        val twin = song("t2b", two.path.uppercase())
        source.catalogue = SourceCatalogue(
            items = listOf(escaping, two, twin),
            groups = listOf(group(ALBUM, ChoiceKind.ALBUM, "t1", "t2", "t2b"))
        )

        val state = run()

        assertFalse(File(library, "escape.flac").exists())
        assertEquals("bytes of t2", fileOf(two).readText().take("bytes of t2".length))
        assertEquals(listOf("t2"), source.opened)
        assertEquals(2, state.failedItems)
    }

    @Test
    fun `covers, extras and playlist files arrive after the items, and a missing image is fine`() {
        choose(ChoiceKind.ALBUM, ALBUM)
        choose(ChoiceKind.PLAYLIST, PLAYLIST)
        source.images[ALBUM] = "album art"
        source.images[PLAYLIST] = "playlist art"
        source.extraFiles = listOf("Music/Air/artist.jpg" to "photo")

        val state = run()

        assertEquals("album art", File(fileOf(one).parentFile, "folder.jpg").readText())
        assertEquals("photo", File(folder, "Music/Air/artist.jpg").readText())
        assertEquals("playlist art", File(folder, "Playlists/p1.jpg").readText())
        val playlist = File(folder, "Playlists/p1.m3u8").readText()
        assertTrue(playlist.contains("../Music/Air/Talkie Walkie/01 Venus.flac"))
        // Talkie Walkie has no image at the source: no cover, and the run still succeeds.
        assertFalse(File(fileOf(three).parentFile, "folder.jpg").exists())
        assertTrue(state.syncComplete)
    }

    @Test
    fun `a run behind another one's lock waits, then runs`() = runBlocking {
        choose(ChoiceKind.ALBUM, ALBUM)
        FolderSetup.lock.lock()
        val running = async {
            SyncRun(
                id, database, choices, tagger, { listOf(platform) }, { _, _ -> folder },
                { library }, { true }
            ).run()
        }
        delay(SETTLE_MS)
        assertTrue(SyncStates.of(id).value.waiting)

        FolderSetup.lock.unlock()
        running.await()

        assertFalse(SyncStates.of(id).value.waiting)
        assertTrue(fileOf(one).exists())
    }

    @Test
    fun `everything chosen with all plans every group of the kind`() {
        choose(ChoiceKind.ALBUM, ALL)

        run()

        assertEquals(listOf("t1", "t2", "t3"), source.opened)
    }
}
