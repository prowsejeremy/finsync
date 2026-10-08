package com.jpd.hz.library.scan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.library.db.LibraryDatabase
import com.jpd.hz.library.db.LibraryScanDao
import com.jpd.hz.tags.Chapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val ALBUM_PATH = "Music/Daft Punk/Discovery"
private const val WAIT_SECONDS = 5L
private const val POLL_MS = 10L

/** Runs whole passes over real files, with fake tags and stamps from the host's file system. */
@RunWith(RobolectricTestRunner::class)
class LibraryScannerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: LibraryDatabase
    private lateinit var dao: LibraryScanDao
    private lateinit var root: File
    private lateinit var libraryFolder: File
    private val tags = FakeTagSource()
    private var decodes = false
    private val passes = AtomicInteger()
    private val ids = AtomicInteger()
    private val artFolder by lazy { File(temp.root, "embedded_art") }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.scanDao()
        root = temp.newFolder("Media", "hz")
        libraryFolder = root
        audio("$ALBUM_PATH/01 One More Time.mp3", "One More Time", number = 1)
        audio("$ALBUM_PATH/02 Aerodynamic.mp3", "Aerodynamic", number = 2)
        file("$ALBUM_PATH/folder.jpg")
        file("Audiobooks/JMC/Hurry/Hurry.m4b")
        tags.tagsByName["Hurry.m4b"] = tagsOf(
            "ALBUM" to "Hurry",
            "ARTIST" to "John Mark Comer",
            chapters = listOf(Chapter("One", 0L), Chapter("Two", 1_000L))
        )
        file("Playlists/Mix.m3u8", "#PLAYLIST:Mix\n../$ALBUM_PATH/02 Aerodynamic.mp3\n")
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun file(path: String, text: String = path): File =
        File(root, path).apply {
            parentFile?.mkdirs()
            writeText(text)
        }

    private fun audio(path: String, title: String, number: Int) {
        file(path)
        tags.tagsByName[File(path).name] = tagsOf(
            "TITLE" to title,
            "ARTIST" to "Daft Punk",
            "ALBUMARTIST" to "Daft Punk",
            "ALBUM" to "Discovery",
            "TRACKNUMBER" to number.toString()
        )
    }

    private val folderChanges = AtomicInteger()

    private fun scanner(stamper: FileStamper = JvmStamper) = LibraryScanner(
        dao = dao,
        folder = {
            passes.incrementAndGet()
            libraryFolder
        },
        stamper = stamper,
        tags = tags,
        decode = DecodeCheck { decodes },
        art = EmbeddedArt(artFolder, tags),
        scope = CoroutineScope(Dispatchers.IO),
        clock = { 42L },
        newId = { "id-${ids.incrementAndGet()}" },
        folderChanges = { folderChanges.get() }
    )

    private fun query(sql: String): List<List<String?>> =
        database.openHelper.readableDatabase.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { cursor.getString(it) })
                }
            }
        }

    // Songs by their path, which is a field of their file (A2).
    private fun trackPaths(): List<String> = query(
        "SELECT f.path FROM library_tracks t INNER JOIN library_files f ON f.fileId = t.trackId " +
            "ORDER BY f.path"
    ).map { it.single()!! }

    // The progress queries arrive with the repositories (M2); the table is the scanner's now.
    private fun saveProgress(bookId: String, positionMs: Long) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO book_progress (bookId, positionMs, finished, lastPlayedAt) " +
                "VALUES (?, ?, 0, 1)",
            arrayOf<Any>(bookId, positionMs)
        )
    }

    private fun idsByPath(): Map<String, String> =
        runBlocking { dao.files() }.associate { it.path to it.fileId }

    @Test
    fun theFirstPassReadsEveryFileAndWritesTheLibrary() {
        val scanner = scanner()

        runBlocking { scanner.runPass() }

        assertEquals(3, tags.reads.size)
        assertEquals(
            listOf("$ALBUM_PATH/01 One More Time.mp3", "$ALBUM_PATH/02 Aerodynamic.mp3"),
            trackPaths()
        )
        assertEquals(
            listOf(listOf("Discovery", "Daft Punk", "$ALBUM_PATH/folder.jpg", null)),
            query("SELECT name, albumArtist, artworkPath, embeddedArt FROM library_albums")
        )
        assertEquals(
            listOf(listOf("Audiobooks/JMC/Hurry/Hurry.m4b", "Hurry", "John Mark Comer")),
            query(
                "SELECT f.path, b.title, b.author FROM library_books b " +
                    "INNER JOIN library_files f ON f.fileId = b.bookId"
            )
        )
        assertEquals(2, runBlocking { dao.chapters() }.size)
        assertEquals(
            listOf(listOf("Playlists/Mix.m3u8", "0", "$ALBUM_PATH/02 Aerodynamic.mp3")),
            query(
                "SELECT p.playlistId, p.position, f.path FROM library_playlist_items p " +
                    "INNER JOIN library_files f ON f.fileId = p.trackId"
            )
        )
        assertEquals(ScanState.Idle(ScanResult(2, 1, 1, 0, 42L)), scanner.state.value)
    }

    @Test
    fun unchangedFilesAreNotReadAgain() {
        val scanner = scanner()

        runBlocking {
            scanner.runPass()
            scanner.runPass()
        }

        assertEquals(3, tags.reads.size)
        assertEquals(2, trackPaths().size)
    }

    @Test
    fun aChangedFileIsReadAgain() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        audio("$ALBUM_PATH/02 Aerodynamic.mp3", "Aero", number = 2)
        val changed = File(root, "$ALBUM_PATH/02 Aerodynamic.mp3")
        changed.setLastModified(changed.lastModified() + 10_000L)

        runBlocking { scanner.runPass() }

        assertEquals(4, tags.reads.size)
        assertEquals(changed.path, tags.reads.last())
        assertEquals(
            listOf(listOf("Aero")),
            query("SELECT title FROM library_tracks WHERE trackNumber = 2")
        )
    }

    @Test
    fun filesThatMovedWithTheirFolderKeepTheirIdsAndRows() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        val before = idsByPath()
        File(root, "kurage").mkdirs()
        for (name in listOf("Music", "Audiobooks", "Playlists")) {
            assertTrue(File(root, name).renameTo(File(root, "kurage/$name")))
        }

        runBlocking { scanner.runPass() }

        assertEquals(3, tags.reads.size)
        assertEquals(
            listOf(
                "kurage/$ALBUM_PATH/01 One More Time.mp3",
                "kurage/$ALBUM_PATH/02 Aerodynamic.mp3"
            ),
            trackPaths()
        )
        assertEquals(
            listOf(listOf("kurage/$ALBUM_PATH/folder.jpg")),
            query("SELECT artworkPath FROM library_albums")
        )
        assertEquals(before.mapKeys { "kurage/${it.key}" }, idsByPath())
        assertEquals(
            listOf(before.getValue("Audiobooks/JMC/Hurry/Hurry.m4b")),
            runBlocking { dao.chapters() }.map { it.bookId }.distinct()
        )
        assertEquals(
            listOf(listOf("kurage/Playlists/Mix.m3u8", "kurage/$ALBUM_PATH/02 Aerodynamic.mp3")),
            query(
                "SELECT p.playlistId, f.path FROM library_playlist_items p " +
                    "INNER JOIN library_files f ON f.fileId = p.trackId"
            )
        )
    }

    @Test
    fun aFileMovedIntoAnAudiobooksFolderIsReadAgainAsABook() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        File(root, "Audiobooks/Music").mkdirs()
        assertTrue(File(root, "Music/Daft Punk").renameTo(File(root, "Audiobooks/Music/Daft Punk")))

        runBlocking { scanner.runPass() }

        assertEquals(5, tags.reads.size)
        assertEquals(emptyList<String>(), trackPaths())
        assertEquals(3, query("SELECT bookId FROM library_books").size)
    }

    @Test
    fun aDeletedFileIsDropped() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        File(root, "$ALBUM_PATH/01 One More Time.mp3").delete()

        runBlocking { scanner.runPass() }

        assertEquals(listOf("$ALBUM_PATH/02 Aerodynamic.mp3"), trackPaths())
    }

    @Test
    fun aFolderThatCantBeReadKeepsTheLibrary() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        libraryFolder = File(root, "renamed")

        runBlocking { scanner.runPass() }

        assertEquals(ScanState.Failed(File(root, "renamed").path), scanner.state.value)
        assertEquals(2, trackPaths().size)
    }

    @Test
    fun anUnreadableFileIsCountedAndNotReadAgainUntilItChanges() {
        file("Mine/broken.mp3")
        val scanner = scanner()

        runBlocking {
            scanner.runPass()
            scanner.runPass()
        }

        assertEquals(4, tags.reads.size)
        assertEquals(ScanState.Idle(ScanResult(2, 1, 1, 1, 42L)), scanner.state.value)
        assertEquals(2, trackPaths().size)
    }

    @Test
    fun anAlbumWithoutArtUsesItsEmbeddedCoverUntilAnArtFileArrives() {
        file("Loose/Album/01.mp3")
        tags.tagsByName["01.mp3"] = tagsOf("ALBUM" to "Loose", "ALBUMARTIST" to "Solo")
        tags.coversByName["01.mp3"] = byteArrayOf(9)
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        val sql = "SELECT artworkPath, embeddedArt FROM library_albums WHERE name = 'Loose'"
        val (path, name) = query(sql).single()
        assertNull(path)
        val cached = File(artFolder, name!!)
        assertTrue(cached.isFile)

        file("Loose/Album/cover.png")
        runBlocking { scanner.runPass() }

        assertEquals(listOf("Loose/Album/cover.png", null), query(sql).single())
        assertFalse(cached.exists())
        // The book has no cover; its "none" record stays, so it isn't read again.
        assertEquals(1, artFolder.list()!!.size)
    }

    @Test
    fun aFileWhoseReadFailsIsLeftOutAndReadAgainNextPass() {
        file("Mine/bad.mp3")
        tags.failingNames.add("bad.mp3")
        val scanner = scanner()

        runBlocking { scanner.runPass() }
        assertEquals(ScanState.Idle(ScanResult(2, 1, 1, 1, 42L)), scanner.state.value)
        tags.failingNames.clear()
        tags.tagsByName["bad.mp3"] = tagsOf("TITLE" to "Fine now")
        runBlocking { scanner.runPass() }

        assertEquals(3, trackPaths().size)
        assertEquals(5, tags.reads.size)
    }

    @Test
    fun rescanReadsUnreadableFilesAgain() {
        file("Mine/broken.mp3")
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        decodes = true

        runBlocking { scanner.runPass() }
        assertEquals(2, trackPaths().size)
        scanner.rescan()
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(WAIT_SECONDS)
        while (System.currentTimeMillis() < deadline && trackPaths().size < 3) Thread.sleep(POLL_MS)

        assertEquals(3, trackPaths().size)
    }

    @Test
    fun aFirstPassThatFailsShowsRetryRatherThanBuildingForEver() {
        // A failure outside any one file's read, such as Room's or the file system's.
        val scanner = scanner(FileStamper { throw IllegalStateException("storage failed") })

        scanner.requestScan()
        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(WAIT_SECONDS)
        while (System.currentTimeMillis() < deadline && scanner.state.value !is ScanState.Failed) {
            Thread.sleep(POLL_MS)
        }

        assertEquals(ScanState.Failed(root.absolutePath), scanner.state.value)
    }

    @Test
    fun aChangedFileKeepsItsId() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        val before = idsByPath()
        val changed = File(root, "$ALBUM_PATH/02 Aerodynamic.mp3")
        changed.setLastModified(changed.lastModified() + 10_000L)

        runBlocking { scanner.runPass() }

        assertEquals(before, idsByPath())
    }

    @Test
    fun aCopiedBookKeepsItsIdAndProgressByItsTags() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        val bookId = idsByPath().getValue("Audiobooks/JMC/Hurry/Hurry.m4b")
        saveProgress(bookId, 60_000L)
        // A copy and a delete give the file a new inode, as a re-download or another storage does.
        val old = File(root, "Audiobooks/JMC/Hurry/Hurry.m4b")
        old.copyTo(File(root, "Audiobooks/Other/Hurry.m4b"))
        old.delete()

        runBlocking { scanner.runPass() }

        assertEquals(bookId, idsByPath()["Audiobooks/Other/Hurry.m4b"])
        assertEquals(
            listOf(listOf(bookId, "60000")),
            query("SELECT bookId, positionMs FROM book_progress")
        )
    }

    @Test
    fun aKeyTwoVanishedFilesShareMatchesNeither() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        val before = idsByPath()
        // Both copies carry the same album, disc, number and title.
        tags.tagsByName["02 Aerodynamic.mp3"] = tags.tagsByName.getValue("01 One More Time.mp3")
        for (name in listOf("01 One More Time.mp3", "02 Aerodynamic.mp3")) {
            val old = File(root, "$ALBUM_PATH/$name")
            old.copyTo(File(root, "Copies/$name"))
            old.delete()
        }

        runBlocking { scanner.runPass() }

        val after = idsByPath()
        assertTrue(after.getValue("Copies/01 One More Time.mp3") !in before.values)
        assertTrue(after.getValue("Copies/02 Aerodynamic.mp3") !in before.values)
    }

    @Test
    fun aRemovedBookTakesItsProgressWithIt() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        val bookId = idsByPath().getValue("Audiobooks/JMC/Hurry/Hurry.m4b")
        saveProgress(bookId, 60_000L)
        File(root, "Audiobooks/JMC/Hurry/Hurry.m4b").delete()

        runBlocking { scanner.runPass() }

        assertEquals(emptyList<List<String?>>(), query("SELECT bookId FROM book_progress"))
    }

    @Test
    fun aKnownFileThatCantBeStampedKeepsItsRowsAndId() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        val before = idsByPath()
        val blind = scanner(FileStamper { file ->
            if (file.name.startsWith("01")) null else JvmStamper.stampOf(file)
        })

        runBlocking { blind.runPass() }

        assertEquals(before, idsByPath())
    }

    @Test
    fun filesUnderAFolderThatCantBeListedKeepTheirRows() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        val before = idsByPath()
        val album = File(root, ALBUM_PATH)
        assertTrue(album.setReadable(false))
        try {
            runBlocking { scanner.runPass() }
        } finally {
            album.setReadable(true)
        }

        assertEquals(before, idsByPath())
    }

    @Test
    fun aFolderWithNoAudioLeftKeepsTheLibraryAndItsProgress() {
        val scanner = scanner()
        runBlocking { scanner.runPass() }
        val bookId = idsByPath().getValue("Audiobooks/JMC/Hurry/Hurry.m4b")
        saveProgress(bookId, 60_000L)
        // As when a Library folder that was moved away is created again, empty.
        root.listFiles()!!.forEach { it.deleteRecursively() }

        runBlocking { scanner.runPass() }

        assertEquals(ScanState.Failed(root.absolutePath), scanner.state.value)
        assertEquals(2, trackPaths().size)
        assertEquals(1, query("SELECT bookId FROM book_progress").size)
    }

    @Test
    fun aPassThatRanAcrossAFolderMoveWritesNothing() {
        val moving = scanner(FileStamper { file ->
            folderChanges.incrementAndGet()
            JvmStamper.stampOf(file)
        })

        val written = runBlocking { moving.runPass() }

        assertFalse(written)
        assertEquals(emptyList<String>(), trackPaths())
    }

    @Test
    fun aRequestDuringAScanRunsExactlyOneMorePass() {
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val scanner = scanner(FileStamper { file ->
            entered.countDown()
            gate.await(WAIT_SECONDS, TimeUnit.SECONDS)
            JvmStamper.stampOf(file)
        })

        scanner.requestScan()
        assertTrue(entered.await(WAIT_SECONDS, TimeUnit.SECONDS))
        scanner.requestScan()
        scanner.requestScan()
        gate.countDown()

        val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(WAIT_SECONDS)
        while (System.currentTimeMillis() < deadline &&
            !(passes.get() == 2 && scanner.state.value is ScanState.Idle)
        ) {
            Thread.sleep(POLL_MS)
        }
        Thread.sleep(POLL_MS * 20)
        assertEquals(2, passes.get())
        assertTrue(scanner.state.value is ScanState.Idle)
    }
}
