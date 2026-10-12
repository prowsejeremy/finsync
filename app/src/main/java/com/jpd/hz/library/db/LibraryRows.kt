package com.jpd.hz.library.db

import androidx.room.Embedded

// Rows the repositories read. Paths are as saved (A1): relative to the Library folder, or a name
// in the embedded-art cache. The repositories turn them into files (LibraryFiles).

/** An album with how many of its tracks are in the library. */
data class AlbumRow(
    val albumId: String,
    val name: String,
    val albumArtist: String?,
    val year: Int?,
    val artworkPath: String?,
    val embeddedArt: String?,
    val trackCount: Int
)

/** One album-artist credit, with the artist's photo. */
data class ArtistCreditRow(
    val artistId: String,
    val name: String,
    val photoPath: String?,
    val albumId: String
)

/** One genre on one track. */
data class GenreTrackRow(
    val genreId: String,
    val name: String,
    val trackId: String,
    val albumId: String?
)

/** A track with its album's name, year and art, for song lists. */
data class SongTrackRow(
    @Embedded val track: LibraryTrack,
    val albumName: String?,
    val albumYear: Int?,
    val artworkPath: String?,
    val embeddedArt: String?
)

/** What the playback resolver needs from the library for one track. */
data class PlayableTrackRow(
    @Embedded val track: LibraryTrack,
    /** The file's path in the Library folder. */
    val path: String,
    val albumName: String?,
    /** The album artists as shown. */
    val albumArtist: String?,
    /** The album's first album artist, for the Player's artist pill. */
    val albumArtistId: String?,
    val artworkPath: String?,
    val embeddedArt: String?
)

/** A book with its file's path in the Library folder. */
data class BookFileRow(
    @Embedded val book: LibraryBook,
    val path: String
)

/** A playlist entry, for the Playlists list's counts and fallback cover. */
data class PlaylistEntryRow(
    val playlistId: String,
    val position: Int,
    val durationMs: Long?,
    val artworkPath: String?,
    val embeddedArt: String?
)
