package com.jpd.finsync.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A track in the server's library. It counts as downloaded when synced_tracks has a row with the
 * same itemId; nothing here stores a second copy of that flag.
 */
@Entity(
    tableName = "catalogue_tracks",
    indices = [Index(value = ["albumId"])]
)
data class CatalogueTrack(
    @PrimaryKey val itemId: String,
    val albumId: String?,
    val name: String,
    val artistNames: List<String>,
    val artistIds: List<String>,
    val albumArtist: String?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val durationMs: Long?,
    val codec: String?,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val size: Long?
)
