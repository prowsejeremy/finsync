package com.jpd.finsync.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "synced_tracks",
    indices = [
        Index(value = ["localPath"], unique = true),
        Index(value = ["albumId"])
    ]
)
data class SyncedTrack(
    @PrimaryKey
    val itemId: String,
    val localPath: String,
    val serverPath: String?,
    val albumId: String?,
    val fileSize: Long,
    val dateModified: String? = null,
    val syncedAt: Long = System.currentTimeMillis()
)
