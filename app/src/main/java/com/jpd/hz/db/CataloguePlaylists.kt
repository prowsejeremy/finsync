package com.jpd.hz.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A playlist on the server with at least one audio entry (3b). */
@Entity(tableName = "catalogue_playlists")
data class CataloguePlaylist(
    @PrimaryKey val playlistId: String,
    val name: String
)

/**
 * A playlist entry. Keyed by position, so a song can appear twice in one playlist. itemId points
 * at catalogue_tracks with no foreign key, as 3a's link tables do.
 */
@Entity(
    tableName = "catalogue_playlist_items",
    primaryKeys = ["playlistId", "position"],
    indices = [Index(value = ["itemId"])]
)
data class CataloguePlaylistItem(
    val playlistId: String,
    val position: Int,
    val itemId: String
)
