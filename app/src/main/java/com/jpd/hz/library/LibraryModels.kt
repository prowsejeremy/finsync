package com.jpd.hz.library

import com.jpd.hz.library.db.LibraryBook
import com.jpd.hz.library.db.LibraryTrack

/** A row in the Albums list. */
data class AlbumSummary(
    val albumId: String,
    val name: String,
    val albumArtist: String?,
    val year: Int?,
    val downloadedTrackCount: Int,
    val artworkPath: String?
)

/** Album detail's data. [tracks] is in disc, number and title order. */
data class AlbumDetail(
    val albumId: String,
    val name: String,
    val albumArtist: String?,
    val year: Int?,
    val artworkPath: String?,
    val tracks: List<LibraryTrack>
)

/** What the playback resolver needs for one track. */
data class PlayableSource(
    val track: LibraryTrack,
    val localPath: String,
    val albumName: String?,
    /** The album artists as shown. */
    val albumArtist: String?,
    val albumArtistId: String?,
    val artworkPath: String?
)

/** A row in Album Artists. [photoPath] is the artist.jpg the scan found, if any. */
data class ArtistSummary(
    val artistId: String,
    val name: String,
    val albumCount: Int,
    val photoPath: String?
)

/** A card in Genres. */
data class GenreSummary(
    val genreId: String,
    val name: String,
    val albumCount: Int,
    val songCount: Int
)

/** A track in All songs or Songs, with its album's name, year and artwork. */
data class SongRow(
    val itemId: String,
    val title: String,
    /** The track's artists joined with ", ", or the album artists when it lists none. */
    val artists: String?,
    val albumId: String?,
    val albumName: String?,
    val albumYear: Int?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val durationMs: Long?,
    val artworkPath: String?
)

/**
 * An artist's or genre's page: albums (newest first) and the All songs list (album order).
 * Genres have no photo.
 */
data class GroupDetail(
    val id: String,
    val name: String,
    val photoPath: String?,
    val albums: List<AlbumSummary>,
    val songs: List<SongRow>,
    val showsAllSongs: Boolean
)

/** A row in Playlists: the songs in the library only. */
data class PlaylistSummary(
    val playlistId: String,
    val name: String,
    val songCount: Int,
    val durationsMs: List<Long?>,
    /** The image beside the playlist file, else the first song's album art. */
    val coverPath: String?
)

/** The playlist page: its songs in file order, a song repeated where it repeats. */
data class PlaylistDetail(
    val playlistId: String,
    val name: String,
    val coverPath: String?,
    val songs: List<SongRow>
)

/** A row in Audio Books. [durationMs] is 0 when the file didn't say. */
data class BookSummary(
    val bookId: String,
    val name: String,
    val author: String?,
    val durationMs: Long,
    val coverPath: String?,
    val status: BookStatus,
    val lastPlayedAt: Long?
)

/** The book page's data. [chapters] is never empty (a book without any has one, its own). */
data class BookDetail(
    val bookId: String,
    val name: String,
    val author: String?,
    val durationMs: Long,
    val coverPath: String?,
    val chapters: List<Chapter>,
    val status: BookStatus
)

/** What the playback resolver needs for one book. [chapters] is never empty. */
data class PlayableBook(
    val book: LibraryBook,
    val localPath: String,
    val coverPath: String?,
    val chapters: List<Chapter>
)
