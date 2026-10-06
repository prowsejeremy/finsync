package com.jpd.hz.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SyncedTrack::class,
        SyncedAlbum::class,
        CatalogueAlbum::class,
        CatalogueTrack::class,
        CatalogueArtist::class,
        CatalogueAlbumArtist::class,
        CatalogueTrackArtist::class,
        CatalogueGenre::class,
        CatalogueTrackGenre::class,
        CataloguePlaylist::class,
        CataloguePlaylistItem::class,
        CatalogueBook::class,
        CatalogueBookChapter::class,
        BookProgress::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class SyncDatabase : RoomDatabase() {

    abstract fun syncDao(): SyncDao

    abstract fun catalogueDao(): CatalogueDao

    companion object {
        @Volatile
        private var INSTANCE: SyncDatabase? = null

        fun getInstance(context: Context): SyncDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SyncDatabase::class.java,
                    "hz_sync.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    // Versions 6 and 7 have no migrations on purpose: the database is rebuilt,
                    // and the next sync recreates download records from files on disk (3a and 3b
                    // specs). Book progress starts empty after a rebuild. This also covers
                    // installs older than version 4.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
