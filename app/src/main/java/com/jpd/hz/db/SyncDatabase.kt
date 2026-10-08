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
        CatalogueTrack::class,
        CataloguePlaylist::class,
        CataloguePlaylistItem::class,
        CatalogueBook::class
    ],
    version = 9,
    exportSchema = false
)
/**
 * The Jellyfin adapter's database (T3): its sync records and its copy of the server's catalogue.
 * The player has its own (LibraryDatabase) and never reads this one.
 */
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
                    .addMigrations(MIGRATION_4_5, MIGRATION_7_8, MIGRATION_8_9)
                    // Versions 6 and 7 have no migrations on purpose: the database is rebuilt,
                    // and the next sync recreates download records from files on disk (3a and 3b
                    // specs). This also covers installs older than version 4.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
