package com.jpd.finsync.db

import androidx.room.Entity
import androidx.room.Index

// Link tables keyed by Jellyfin IDs. There are no foreign keys: replaceCatalogue swaps every
// catalogue table in one transaction, so links never point into an older catalogue.

/** An album's album artists; [position] keeps the order they were first seen in. */
@Entity(
    tableName = "catalogue_album_artists",
    primaryKeys = ["albumId", "artistId"],
    indices = [Index(value = ["artistId"])]
)
data class CatalogueAlbumArtist(
    val albumId: String,
    val artistId: String,
    val position: Int
)

/** A track's artists; [position] keeps the server's order. */
@Entity(
    tableName = "catalogue_track_artists",
    primaryKeys = ["itemId", "artistId"],
    indices = [Index(value = ["artistId"])]
)
data class CatalogueTrackArtist(
    val itemId: String,
    val artistId: String,
    val position: Int
)

@Entity(
    tableName = "catalogue_track_genres",
    primaryKeys = ["itemId", "genreId"],
    indices = [Index(value = ["genreId"])]
)
data class CatalogueTrackGenre(
    val itemId: String,
    val genreId: String
)
