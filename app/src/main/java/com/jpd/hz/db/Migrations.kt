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
