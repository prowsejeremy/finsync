package com.jpd.hz.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An audiobook on the server: one file with chapters (3b). The audio details follow
 * catalogue_tracks, so the Player's info row works unchanged.
 */
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

/** One of a book's chapters, in start order. */
@Entity(
    tableName = "catalogue_book_chapters",
    primaryKeys = ["bookId", "position"]
)
data class CatalogueBookChapter(
    val bookId: String,
    val position: Int,
    val name: String,
    val startMs: Long
)
