package com.jpd.finsync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "synced_albums")
data class SyncedAlbum(
    @PrimaryKey val albumId: String,
    val name: String,
    val albumArtist: String?,
    val childCount: Int,
    val artworkPath: String? = null
)
