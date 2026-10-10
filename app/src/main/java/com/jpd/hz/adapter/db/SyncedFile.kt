package com.jpd.hz.adapter.db

import androidx.room.Entity
import androidx.room.Index

/**
 * A file one connection's sync wrote (spec "Data"). [path] is relative to the connection's folder
 * (A1). [version] is the source's at download. [fileSize] is the size after tagging, and
 * [tagFingerprint] the fields last written, or null when the file has none of ours (T2).
 */
@Entity(
    tableName = "synced_files",
    primaryKeys = ["connectionId", "itemId"],
    indices = [Index(value = ["connectionId", "path"], unique = true)]
)
data class SyncedFile(
    val connectionId: String,
    val itemId: String,
    val path: String,
    val version: String?,
    val fileSize: Long,
    val tagFingerprint: String? = null,
    val syncedAt: Long = System.currentTimeMillis()
)

/** A record's item and path. */
data class RecordPath(val itemId: String, val path: String)

/** A record's path, relative to its connection's folder, and the file's size after tagging. */
data class RecordSize(val path: String, val fileSize: Long)
