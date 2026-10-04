package com.jpd.finsync.library

import com.jpd.finsync.db.CatalogueBook
import com.jpd.finsync.db.CatalogueTrack

/** A row in the Albums list. */
data class AlbumSummary(
    val albumId: String,
    val name: String,
    val albumArtist: String?,
    val year: Int?,
    val downloadedTrackCount: Int,
    val artworkPath: String?
)

/** Album detail's data. [tracks] holds downloaded tracks only, in disc, number and name order. */
data class AlbumDetail(
    val albumId: String,
    val name: String,
    val albumArtist: String?,
    val year: Int?,
    val artworkPath: String?,
    val tracks: List<CatalogueTrack>
)

/** What the playback resolver needs for one downloaded track. */
data class PlayableSource(
    val track: CatalogueTrack,
    val localPath: String,
    val albumName: String?,
    val artworkPath: String?
)

/** A row in Album Artists. [photoPath] is set only when the photo file exists. */
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

/** A downloaded track in All songs or Songs, with its album's name, year and artwork. */
data class SongRow(
    val itemId: String,
    val title: String,
    /** The track's artists joined with ", ", or the album artist when it lists none. */
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
 * An artist's or genre's page: visible albums (newest first) and the All songs list (album
 * order). Genres have no photo.
 */
data class GroupDetail(
    val id: String,
    val name: String,
    val photoPath: String?,
    val albums: List<AlbumSummary>,
    val songs: List<SongRow>,
    val showsAllSongs: Boolean
)

/** A row in Playlists: its downloaded songs only. */
data class PlaylistSummary(
    val playlistId: String,
    val name: String,
    val songCount: Int,
    val durationsMs: List<Long?>,
    /** The server's cover if sync fetched it, else the first downloaded song's album art. */
    val coverPath: String?
)

/** The playlist page: downloaded entries in server order, a song repeated where it repeats. */
data class PlaylistDetail(
    val playlistId: String,
    val name: String,
    val coverPath: String?,
    val songs: List<SongRow>
)

/** A playlist in Playlists to Sync, with its audio entry count on the server. */
data class PlaylistChoice(
    val playlistId: String,
    val name: String,
    val songCount: Int
)

/** A row in Audio Books. [durationMs] is 0 when the server didn't say. */
data class BookSummary(
    val bookId: String,
    val name: String,
    val author: String?,
    val durationMs: Long,
    val coverPath: String?,
    val status: BookStatus,
    val lastPlayedAt: Long?
)

/** The book page's data. [chapters] is never empty (spec "No chapters from the server"). */
data class BookDetail(
    val bookId: String,
    val name: String,
    val author: String?,
    val durationMs: Long,
    val coverPath: String?,
    val chapters: List<Chapter>,
    val status: BookStatus
)

/** A book in Books to Sync, with its file size. */
data class BookChoice(
    val bookId: String,
    val name: String,
    val author: String?,
    val sizeBytes: Long?
)

/** What the playback resolver needs for one downloaded book. [chapters] is never empty. */
data class PlayableBook(
    val book: CatalogueBook,
    val localPath: String,
    val coverPath: String?,
    val chapters: List<Chapter>
)
