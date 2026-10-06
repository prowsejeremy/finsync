package com.jpd.hz.ui

import android.content.res.Resources
import com.jpd.hz.R
import com.jpd.hz.library.BookStatus
import java.util.Locale

private const val BYTES_PER_MB = 1_000_000.0
private const val BYTES_PER_GB = 1_000_000_000.0

/** The speed pill and sheet: "1.2×", "2.0×", or "1.75×" when a second decimal is needed. */
fun formatSpeed(speed: Float): String {
    val twoDecimals = String.format(Locale.ROOT, "%.2f", speed)
    val shown = if (twoDecimals.endsWith("0")) twoDecimals.dropLast(1) else twoDecimals
    return "$shown×"
}

/** A file size for Books to Sync: "412 MB", or "1.2 GB" from a gigabyte (decision 21). */
fun formatFileSize(bytes: Long): String =
    if (bytes >= BYTES_PER_GB) {
        String.format(Locale.ROOT, "%.1f GB", bytes / BYTES_PER_GB)
    } else {
        "${Math.round(bytes / BYTES_PER_MB)} MB"
    }

/** "Chapter 7 · 8 h 28 min left", "Not started · 11 h 5 min" or "Finished" (spec). */
fun bookStatusText(resources: Resources, status: BookStatus): String = when (status) {
    is BookStatus.InProgress -> {
        val left = formatListLength(resources, listOf(status.leftMs))
        joinWithDots(
            listOf(
                resources.getString(R.string.book_chapter_number, status.chapterNumber),
                resources.getString(R.string.book_time_left, left)
            )
        )
    }
    is BookStatus.NotStarted -> joinWithDots(
        listOf(
            resources.getString(R.string.book_not_started),
            formatListLength(resources, listOf(status.durationMs))
        )
    )
    BookStatus.Finished -> resources.getString(R.string.book_finished)
}
