package com.jpd.hz.adapter.run

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.SourceItem
import com.jpd.hz.adapter.choices.ChoiceStore
import com.jpd.hz.adapter.db.SyncDatabase
import com.jpd.hz.adapter.files.FakeTagger
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

/**
 * The storage sketch (adapter harness spec, "Fits the harness"): a NAS share or USB drive's
 * folder, copied in as it is. Its items are the share's own files, by path; its groups are its
 * top folders; it writes no tags. It runs through the same harness with no change, which is the
 * point: if this needed one, the harness wouldn't be platform-agnostic.
 */
@RunWith(RobolectricTestRunner::class)
class FolderSourceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: SyncDatabase
    private lateinit var choices: ChoiceStore
    private lateinit var share: File
    private lateinit var library: File
    private val tagger = FakeTagger()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        choices = ChoiceStore(context)
        share = temp.newFolder("share")
        library = temp.newFolder("library")
        write("Jazz/Kind of Blue/01 So What.flac", "so what")
        write("Jazz/Kind of Blue/folder.jpg", "cover")
        write("Jazz/Mix.m3u8", "#EXTM3U\nKind of Blue/01 So What.flac\n")
        write("Rock/Doolittle/01 Debaser.mp3", "debaser")
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun write(path: String, text: String) =
        File(share, path).apply { parentFile!!.mkdirs(); writeText(text) }

    /** The sketch's catalogue: a walk of the share; size and mtime as the version. */
    private fun walk(): SourceCatalogue {
        val files = share.walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()
        val items = files.map { file ->
            val path = file.relativeTo(share).invariantSeparatorsPath
            SourceItem(
                id = path,
                path = path,
                version = "${file.length()}:${file.lastModified()}",
                kind = ItemKind.MUSIC,
                label = file.nameWithoutExtension,
                durationMs = null,
                size = file.length(),
                fields = null
            )
        }
        val groups = items.groupBy { it.path.substringBefore('/') }.map { (top, inFolder) ->
            ChoiceGroup(top, ChoiceKind.FOLDER, top, null, inFolder.map { it.id })
        }
        return SourceCatalogue(items, groups)
    }

    // The sketch's platform: one share, chosen by folder, read file by file.
    private fun nasPlatform() = FakePlatform(
        FakeSource(walk()) { item -> File(share, item.path).inputStream() },
        key = "nas",
        choiceKinds = listOf(ChoiceKind.FOLDER)
    )

    private fun sync(platform: FakePlatform) = runBlocking {
        SyncRun(
            platform.connection.id, database, choices, tagger, { listOf(platform) },
            { _, _ -> File(library, "nas") }, { library }, { true }
        ).run()
        SyncStates.of(platform.connection.id).value
    }

    @Test
    fun `a chosen folder is copied as it is, untagged, and only it`() {
        val platform = nasPlatform()
        choices.setChosen(platform.connection.id, ChoiceKind.FOLDER, setOf("Jazz"))

        val state = sync(platform)

        val copied = File(library, "nas")
        assertTrue(state.syncComplete)
        assertEquals("so what", File(copied, "Jazz/Kind of Blue/01 So What.flac").readText())
        assertEquals("cover", File(copied, "Jazz/Kind of Blue/folder.jpg").readText())
        assertTrue(File(copied, "Jazz/Mix.m3u8").exists())
        assertFalse(File(copied, "Rock").exists())
        assertTrue(tagger.writes.isEmpty())
        val record = runBlocking {
            database.recordDao().get(platform.connection.id, "Jazz/Kind of Blue/01 So What.flac")
        }
        assertNull(record?.tagFingerprint)
    }

    @Test
    fun `a changed file on the share is copied again`() {
        val first = nasPlatform()
        choices.setChosen(first.connection.id, ChoiceKind.FOLDER, setOf("Rock"))
        sync(first)
        write("Rock/Doolittle/01 Debaser.mp3", "debaser, remastered")

        sync(nasPlatform())

        assertEquals(
            "debaser, remastered",
            File(library, "nas/Rock/Doolittle/01 Debaser.mp3").readText()
        )
    }
}
