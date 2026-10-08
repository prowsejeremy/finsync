package com.jpd.hz.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.db.CatalogueTrack
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.db.SyncedTrack
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private const val PATH = "Music/Darci/Escape Cycle/01 High Speeds.m4a"
private const val GONE = "Music/Darci/Escape Cycle/02 Gone.m4a"
// The first server's files, in its own folder. A path both servers record can't be held twice:
// synced_tracks.localPath is unique, so the later record replaces the earlier one.
private const val KURAGE_PATH = "Music/Calvin Harris/18 Months/01 Green Valley.m4a"
private const val KURAGE_GONE = "Music/Calvin Harris/18 Months/02 Bounce.m4a"

/** The sync's record pass, with two servers' records in one in-memory database (T4). */
@RunWith(RobolectricTestRunner::class)
class SyncRecordPassTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var database: SyncDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun catalogueTrack(itemId: String) = CatalogueTrack(
        itemId, "album", "Track", listOf("Darci"), emptyList(), "Darci", null, 1, null,
        null, null, null, null, null
    )

    @Test
    fun `only the signed-in server's records of missing files go`() = runBlocking {
        val dao = database.syncDao()
        // The signed-in server's folder holds one of its two files; the catalogue is its own.
        val folder = temp.newFolder("other")
        File(folder, PATH).apply { parentFile?.mkdirs() }.writeText("x")
        dao.upsertTrack(SyncedTrack("other-1", PATH, null, "album", 1L))
        dao.upsertTrack(SyncedTrack("other-2", GONE, null, "album", 1L))
        database.catalogueDao()
            .insertTracks(listOf(catalogueTrack("other-1"), catalogueTrack("other-2")))
        // The first server's records name files in its own folder, none in this one.
        dao.upsertTrack(SyncedTrack("kurage-1", KURAGE_PATH, null, "album", 1L))
        dao.upsertTrack(SyncedTrack("kurage-2", KURAGE_GONE, null, "album", 1L))

        dropRecordsOfMissingFiles(dao, folder)

        assertEquals(
            listOf("kurage-1", "kurage-2", "other-1"),
            dao.allItemIds().sorted()
        )
    }
}
