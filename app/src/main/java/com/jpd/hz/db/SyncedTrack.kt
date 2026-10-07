package com.jpd.hz.db

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
    val syncedAt: Long = System.currentTimeMillis(),
    /**
     * TagFingerprint of the fields sync last wrote into the file (version 8, T2). Null until the
     * file is tagged, so a sync whose fields give another fingerprint re-tags it.
     */
    val tagFingerprint: String? = null
)
