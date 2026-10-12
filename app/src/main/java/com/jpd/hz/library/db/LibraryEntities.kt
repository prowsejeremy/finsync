package com.jpd.hz.library.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// The player's library (spec "LibraryDatabase", amended by A1–A3). An audio file's ID is its
// fileId, given when a scan first sees it, so it survives moves; its path is a field. Artists and
// genres are keyed by normalised name, albums by album artists and name (D8), and playlists by
// their path. Paths are relative to the Library folder. There are no foreign keys: one
// transaction replaces the scan's tables, so links never point into an older scan.

/** What a scanned audio file turned out to be. */
enum class FileKind { TRACK, BOOK, UNREADABLE }

/**
 * One audio file the last scan saw, with the stamps that say whether it has changed. Times are
 * whole seconds. A folder rename leaves its files' stamps and inode alone, so a moved file keeps
 * its ID and rows. An unreadable file isn't read again until it changes, or Rescan.
 */
@Entity(tableName = "library_files", indices = [Index(value = ["path"], unique = true)])
data class LibraryFile(
    @PrimaryKey val fileId: String,
    /** Relative to the Library folder, with "/" separators. */
    val path: String,
    val size: Long,
    val modifiedSec: Long,
    val changedSec: Long,
    val inode: Long,
    val kind: FileKind
)

/** A music file's values as read, so an unchanged file is never read again. */
@Entity(tableName = "library_tracks", indices = [Index(value = ["albumId"])])
data class LibraryTrack(
    /** The file's fileId. */
    @PrimaryKey val trackId: String,
    val title: String,
    val artistNames: List<String>,
    val album: String?,
    /** After the fallback to the first track artist. */
    val albumArtistNames: List<String>,
    val genreNames: List<String>,
    val year: Int?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val durationMs: Long?,
    val codec: String?,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val size: Long,
    /** Null for a track with no album, which shows in Songs but in no album. */
    val albumId: String?
)

/**
 * An album. Its art is a file beside its first track ([artworkPath], relative to the Library
 * folder) or else the cover inside that track ([embeddedArt], a file in the embedded-art cache).
 */
@Entity(tableName = "library_albums")
data class LibraryAlbum(
    @PrimaryKey val albumId: String,
    val name: String,
    /** The album artists joined with ", ", as screens show them. */
    val albumArtist: String?,
    val year: Int?,
    val artworkPath: String?,
    val embeddedArt: String?
)

@Entity(tableName = "library_artists")
data class LibraryArtist(
    @PrimaryKey val artistId: String,
    val name: String,
    /** artist.jpg or artist.png, relative to the Library folder. */
    val photoPath: String?
)

/** An album's album artists; [position] keeps their order in the tags. */
@Entity(
    tableName = "library_album_artists",
    primaryKeys = ["albumId", "artistId"],
    indices = [Index(value = ["artistId"])]
)
data class LibraryAlbumArtist(
    val albumId: String,
    val artistId: String,
    val position: Int
)

/** A track's artists; [position] keeps their order in the tags. */
@Entity(
    tableName = "library_track_artists",
    primaryKeys = ["trackId", "artistId"],
    indices = [Index(value = ["artistId"])]
)
data class LibraryTrackArtist(
    val trackId: String,
    val artistId: String,
    val position: Int
)

@Entity(tableName = "library_genres")
data class LibraryGenre(
    @PrimaryKey val genreId: String,
    val name: String
)

@Entity(
    tableName = "library_track_genres",
    primaryKeys = ["trackId", "genreId"],
    indices = [Index(value = ["genreId"])]
)
data class LibraryTrackGenre(
    val trackId: String,
    val genreId: String
)

/**
 * A playlist file, keyed by its path: nothing is saved against a playlist, so a moved one is
 * simply a new playlist. Its entries that match no scanned song are left out.
 */
@Entity(tableName = "library_playlists")
data class LibraryPlaylist(
    @PrimaryKey val playlistId: String,
    val name: String,
    /** An image named after the playlist file, beside it, relative to the Library folder. */
    val coverPath: String?
)

/** A playlist entry. Keyed by position, so a song can appear twice in one playlist. */
@Entity(
    tableName = "library_playlist_items",
    primaryKeys = ["playlistId", "position"],
    indices = [Index(value = ["trackId"])]
)
data class LibraryPlaylistItem(
    val playlistId: String,
    val position: Int,
    val trackId: String
)

/** A book file's values as read. The audio details follow library_tracks. */
@Entity(tableName = "library_books")
data class LibraryBook(
    /** The file's fileId. */
    @PrimaryKey val bookId: String,
    val title: String,
    /** The authors joined with ", ", or null when the tags name none. */
    val author: String?,
    val durationMs: Long?,
    val codec: String?,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val size: Long,
    /** folder.jpg and the like beside the book, relative to the Library folder. */
    val coverPath: String?,
    /** Else the cover inside the file, by its name in the embedded-art cache. */
    val embeddedCover: String?
)

/** One of a book's chapters, in start order. */
@Entity(
    tableName = "library_book_chapters",
    primaryKeys = ["bookId", "position"]
)
data class LibraryBookChapter(
    val bookId: String,
    val position: Int,
    val name: String,
    val startMs: Long
)

/**
 * How far into a book the user is (A3). It isn't part of a scan: the scan's write keeps it, and
 * deletes it only when its book has gone from the library.
 */
@Entity(tableName = "book_progress")
data class BookProgress(
    @PrimaryKey val bookId: String,
    val positionMs: Long,
    val finished: Boolean,
    val lastPlayedAt: Long
)
