package com.jpd.finsync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How far into a book the user is. Not part of the catalogue: a catalogue refresh never touches
 * it, deselecting a book keeps it, and logout clears it (spec "Book progress").
 */
@Entity(tableName = "book_progress")
data class BookProgress(
    @PrimaryKey val bookId: String,
    val positionMs: Long,
    val finished: Boolean,
    val lastPlayedAt: Long
)
