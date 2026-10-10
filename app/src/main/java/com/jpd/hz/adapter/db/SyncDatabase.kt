package com.jpd.hz.adapter.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * The adapters' database (spec "Data"): each connection's sync records and its copy of its
 * source's catalogue, keyed by connection. The player has its own (LibraryDatabase) and never
 * reads this one.
 *
 * Version 10 is a rebuild (spec H5): earlier versions are dropped, and the first sync re-links
 * the files already on disk and re-tags each once.
 */
@Database(
    entities = [
        SyncedFile::class,
        CatalogueItem::class,
        CatalogueGroup::class,
        CatalogueGroupItem::class
    ],
    version = 10,
    exportSchema = false
)
abstract class SyncDatabase : RoomDatabase() {

    abstract fun recordDao(): RecordDao

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
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
