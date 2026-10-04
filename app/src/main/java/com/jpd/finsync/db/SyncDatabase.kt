package com.jpd.finsync.db

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
        CatalogueTrack::class
    ],
    version = 5,
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
                    "finsync_sync.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    // Safety net for installs older than version 4, which have no migration path.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
