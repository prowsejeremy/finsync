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
    val storedArtworkPath: String?
)
