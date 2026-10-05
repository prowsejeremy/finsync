package com.jpd.finsync.db

import androidx.room.Embedded

/** A catalogue album with at least one downloaded track, before the selection rule applies. */
data class AlbumSummaryRow(
    val albumId: String,
    val name: String,
    val albumArtist: String?,
    val year: Int?,
    val downloadedCount: Int,
    val storedArtworkPath: String?,
    val firstTrackPath: String?
)

data class AlbumHeaderRow(
    val albumId: String,
    val name: String,
    val albumArtist: String?,
    val year: Int?,
    val storedArtworkPath: String?
)

data class DownloadedTrackRow(
    @Embedded val track: CatalogueTrack,
    val localPath: String
)

data class PlayableTrackRow(
    @Embedded val track: CatalogueTrack,
    val localPath: String,
    val albumName: String?,
    /** The album's first album artist, for the Player's artist pill. */
    val albumArtistId: String?,
    val storedArtworkPath: String?
)

/** One album-artist credit on an album with a downloaded track. */
data class ArtistAlbumRow(
    val artistId: String,
    val name: String,
    val albumId: String
)

/** One genre tag on a downloaded track. */
data class GenreTrackRow(
    val genreId: String,
    val name: String,
    val itemId: String,
    val albumId: String?
)

/** A downloaded track with its album's name, year and stored artwork, for song lists. */
data class SongTrackRow(
    @Embedded val track: CatalogueTrack,
    val localPath: String,
    val albumName: String?,
    val albumYear: Int?,
    val storedArtworkPath: String?
)

/** A downloaded entry of a playlist, for the Playlists list's counts and fallback cover. */
data class PlaylistEntryRow(
    val playlistId: String,
    val position: Int,
    val durationMs: Long?,
    val albumId: String?,
    val localPath: String,
    val storedArtworkPath: String?
)

/** A playlist and how many audio entries it has on the server, for Playlists to Sync. */
data class PlaylistChoiceRow(
    val playlistId: String,
    val name: String,
    val entryCount: Int
)

/** An album holding a downloaded entry of a playlist, for the visibility rule. */
data class PlaylistAlbumRow(
    val playlistId: String,
    val albumId: String
)

/** A catalogue track's ID and album (null for none), for the Sync card's counts. */
data class TrackAlbumRow(
    val itemId: String,
    val albumId: String?
)

/** A book with its downloaded file (its synced_tracks row). */
data class DownloadedBookRow(
    @Embedded val book: CatalogueBook,
    val localPath: String
)
