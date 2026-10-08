package com.jpd.hz.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Copied from Room's generated SyncDatabase_Impl.createAllTables, so the migrated schema passes
// Room's validation. The sync tables are untouched, so nobody needs to re-sync after updating.
private const val CREATE_CATALOGUE_ALBUMS =
    "CREATE TABLE IF NOT EXISTS `catalogue_albums` (`albumId` TEXT NOT NULL, " +
        "`name` TEXT NOT NULL, `albumArtist` TEXT, `year` INTEGER, PRIMARY KEY(`albumId`))"

private const val CREATE_CATALOGUE_TRACKS =
    "CREATE TABLE IF NOT EXISTS `catalogue_tracks` (`itemId` TEXT NOT NULL, `albumId` TEXT, " +
        "`name` TEXT NOT NULL, `artistNames` TEXT NOT NULL, `artistIds` TEXT NOT NULL, " +
        "`albumArtist` TEXT, `discNumber` INTEGER, `trackNumber` INTEGER, " +
        "`durationMs` INTEGER, `codec` TEXT, `bitDepth` INTEGER, `sampleRate` INTEGER, " +
        "`bitrate` INTEGER, `size` INTEGER, PRIMARY KEY(`itemId`))"

private const val CREATE_CATALOGUE_TRACKS_ALBUM_INDEX =
    "CREATE INDEX IF NOT EXISTS `index_catalogue_tracks_albumId` " +
        "ON `catalogue_tracks` (`albumId`)"

/** Adds the catalogue tables (database version 5). */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_CATALOGUE_ALBUMS)
        db.execSQL(CREATE_CATALOGUE_TRACKS)
        db.execSQL(CREATE_CATALOGUE_TRACKS_ALBUM_INDEX)
    }
}

private const val ADD_TAG_FINGERPRINT =
    "ALTER TABLE `synced_tracks` ADD COLUMN `tagFingerprint` TEXT"

/**
 * Adds synced_tracks.tagFingerprint (version 8, T2). A real migration, because book_progress
 * held data no sync can restore. Every row starts null, so the next sync tags each file once.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(ADD_TAG_FINGERPRINT)
    }
}

// What the adapter no longer reads once the player reads the Library folder (T3). Their indexes
// go with them.
private val TABLES_DROPPED_IN_9 = listOf(
    "catalogue_albums",
    "catalogue_artists",
    "catalogue_album_artists",
    "catalogue_track_artists",
    "catalogue_genres",
    "catalogue_track_genres",
    "catalogue_book_chapters",
    "book_progress"
)

/**
 * Drops the player's old tables (version 9, T3). The sync records stay, so nothing downloads
 * again. Book progress isn't carried over to the library (the user's note, 2026-10-08).
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        TABLES_DROPPED_IN_9.forEach { db.execSQL("DROP TABLE IF EXISTS `$it`") }
    }
}
