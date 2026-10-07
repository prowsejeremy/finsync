package com.jpd.hz.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val DATABASE = "migration-test.db"

// Room's createAllTables for version 7, copied from the generated SyncDatabase_Impl at 23bcac1.
private val VERSION_7_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `synced_tracks` (`itemId` TEXT NOT NULL, `localPath` TEXT NOT NULL, `serverPath` TEXT, `albumId` TEXT, `fileSize` INTEGER NOT NULL, `dateModified` TEXT, `syncedAt` INTEGER NOT NULL, PRIMARY KEY(`itemId`))",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_synced_tracks_localPath` ON `synced_tracks` (`localPath`)",
    "CREATE INDEX IF NOT EXISTS `index_synced_tracks_albumId` ON `synced_tracks` (`albumId`)",
    "CREATE TABLE IF NOT EXISTS `synced_albums` (`albumId` TEXT NOT NULL, `name` TEXT NOT NULL, `albumArtist` TEXT, `childCount` INTEGER NOT NULL, `artworkPath` TEXT, PRIMARY KEY(`albumId`))",
    "CREATE TABLE IF NOT EXISTS `catalogue_albums` (`albumId` TEXT NOT NULL, `name` TEXT NOT NULL, `albumArtist` TEXT, `year` INTEGER, PRIMARY KEY(`albumId`))",
    "CREATE TABLE IF NOT EXISTS `catalogue_tracks` (`itemId` TEXT NOT NULL, `albumId` TEXT, `name` TEXT NOT NULL, `artistNames` TEXT NOT NULL, `artistIds` TEXT NOT NULL, `albumArtist` TEXT, `discNumber` INTEGER, `trackNumber` INTEGER, `durationMs` INTEGER, `codec` TEXT, `bitDepth` INTEGER, `sampleRate` INTEGER, `bitrate` INTEGER, `size` INTEGER, PRIMARY KEY(`itemId`))",
    "CREATE INDEX IF NOT EXISTS `index_catalogue_tracks_albumId` ON `catalogue_tracks` (`albumId`)",
    "CREATE TABLE IF NOT EXISTS `catalogue_artists` (`artistId` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`artistId`))",
    "CREATE TABLE IF NOT EXISTS `catalogue_album_artists` (`albumId` TEXT NOT NULL, `artistId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`albumId`, `artistId`))",
    "CREATE INDEX IF NOT EXISTS `index_catalogue_album_artists_artistId` ON `catalogue_album_artists` (`artistId`)",
    "CREATE TABLE IF NOT EXISTS `catalogue_track_artists` (`itemId` TEXT NOT NULL, `artistId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`itemId`, `artistId`))",
    "CREATE INDEX IF NOT EXISTS `index_catalogue_track_artists_artistId` ON `catalogue_track_artists` (`artistId`)",
    "CREATE TABLE IF NOT EXISTS `catalogue_genres` (`genreId` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`genreId`))",
    "CREATE TABLE IF NOT EXISTS `catalogue_track_genres` (`itemId` TEXT NOT NULL, `genreId` TEXT NOT NULL, PRIMARY KEY(`itemId`, `genreId`))",
    "CREATE INDEX IF NOT EXISTS `index_catalogue_track_genres_genreId` ON `catalogue_track_genres` (`genreId`)",
    "CREATE TABLE IF NOT EXISTS `catalogue_playlists` (`playlistId` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`playlistId`))",
    "CREATE TABLE IF NOT EXISTS `catalogue_playlist_items` (`playlistId` TEXT NOT NULL, `position` INTEGER NOT NULL, `itemId` TEXT NOT NULL, PRIMARY KEY(`playlistId`, `position`))",
    "CREATE INDEX IF NOT EXISTS `index_catalogue_playlist_items_itemId` ON `catalogue_playlist_items` (`itemId`)",
    "CREATE TABLE IF NOT EXISTS `catalogue_books` (`bookId` TEXT NOT NULL, `name` TEXT NOT NULL, `author` TEXT, `durationMs` INTEGER, `codec` TEXT, `bitDepth` INTEGER, `sampleRate` INTEGER, `bitrate` INTEGER, `size` INTEGER, PRIMARY KEY(`bookId`))",
    "CREATE TABLE IF NOT EXISTS `catalogue_book_chapters` (`bookId` TEXT NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, `startMs` INTEGER NOT NULL, PRIMARY KEY(`bookId`, `position`))",
    "CREATE TABLE IF NOT EXISTS `book_progress` (`bookId` TEXT NOT NULL, `positionMs` INTEGER NOT NULL, `finished` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`bookId`))",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '83eb121a6bbfd6dbf7899d016f98d94f')"
)

/**
 * SyncDatabase v7 → v8 (T2). The schema isn't exported, so Room's MigrationTestHelper can't
 * build version 7. This test builds a real version 7 file by hand and opens it through Room with
 * only the migration: no destructive fallback, so a missing or wrong migration throws.
 */
@RunWith(RobolectricTestRunner::class)
class SyncDatabaseMigrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE)
    }

    private fun createVersion7() {
        val file = context.getDatabasePath(DATABASE)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            VERSION_7_SCHEMA.forEach(db::execSQL)
            db.insertOrThrow("synced_tracks", null, ContentValues().apply {
                put("itemId", "t1")
                put("localPath", "/sync/Music/Daft Punk/Discovery/01 One More Time.flac")
                put("serverPath", "/srv/music/01 One More Time.flac")
                put("albumId", "alb1")
                put("fileSize", 1234L)
                put("syncedAt", 99L)
            })
            db.insertOrThrow("book_progress", null, ContentValues().apply {
                put("bookId", "b1")
                put("positionMs", 5_000L)
                put("finished", 0)
                put("lastPlayedAt", 42L)
            })
            db.version = 7
        } finally {
            db.close()
        }
    }

    @Test
    fun `version 7 opens as version 8 and keeps its rows and book progress`() {
        createVersion7()
        val database = Room.databaseBuilder(context, SyncDatabase::class.java, DATABASE)
            .addMigrations(MIGRATION_7_8)
            .allowMainThreadQueries()
            .build()
        try {
            runBlocking {
                val track = database.syncDao().getTrack("t1")!!
                assertEquals("/sync/Music/Daft Punk/Discovery/01 One More Time.flac", track.localPath)
                assertEquals(1234L, track.fileSize)
                assertEquals(99L, track.syncedAt)
                assertNull(track.tagFingerprint)

                val progress = database.catalogueDao().bookProgress("b1")!!
                assertEquals(5_000L, progress.positionMs)
                assertEquals(42L, progress.lastPlayedAt)

                database.syncDao().upsertTrack(track.copy(tagFingerprint = "ab12"))
                assertEquals("ab12", database.syncDao().getTrack("t1")?.tagFingerprint)
            }
        } finally {
            database.close()
        }
    }
}
