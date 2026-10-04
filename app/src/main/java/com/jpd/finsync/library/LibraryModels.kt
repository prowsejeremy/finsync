package com.jpd.finsync.library

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
