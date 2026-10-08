package com.jpd.hz.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** An audiobook on the server, for Books to Sync and the Sync card's counts. */
@Entity(tableName = "catalogue_books")
data class CatalogueBook(
    @PrimaryKey val bookId: String,
    val name: String,
    val author: String?,
    val durationMs: Long?,
    val codec: String?,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val size: Long?
)
