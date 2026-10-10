package com.jpd.hz.library.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jpd.hz.library.db.StringListConverter

/**
 * The player's database (A3): what the scanner found in the Library folder, and book progress. A
 * rescan updates it in place. Book progress can't be rebuilt, so every schema change needs a real
 * migration: there's no destructive fallback.
 */
@Database(
    entities = [
        LibraryFile::class,
        LibraryTrack::class,
        LibraryAlbum::class,
        LibraryArtist::class,
        LibraryAlbumArtist::class,
        LibraryTrackArtist::class,
        LibraryGenre::class,
        LibraryTrackGenre::class,
        LibraryPlaylist::class,
        LibraryPlaylistItem::class,
        LibraryBook::class,
        LibraryBookChapter::class,
        BookProgress::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class LibraryDatabase : RoomDatabase() {

    abstract fun scanDao(): LibraryScanDao

    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile
        private var INSTANCE: LibraryDatabase? = null

        fun getInstance(context: Context): LibraryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "hz_library.db"
                ).build().also { INSTANCE = it }
            }
    }
}
