package com.jpd.hz.ui

import android.content.res.Resources
import com.jpd.hz.R
import com.jpd.hz.library.scan.ScanState

/**
 * The scan card's status: "Scanning 1,240 of 5,300", "Can't read Media/hz", or the last result.
 * [displayPathOf] names a folder as screens do.
 */
fun Resources.scanStatusLine(state: ScanState, displayPathOf: (String) -> String): String =
    when (state) {
        is ScanState.Scanning -> if (state.total == 0) {
            getString(R.string.library_scan_looking)
        } else {
            getQuantityString(
                R.plurals.library_scan_progress,
                state.total,
                formatCount(state.done),
                formatCount(state.total)
            )
        }
        is ScanState.Failed ->
            getString(R.string.home_library_unreadable, displayPathOf(state.folder))
        is ScanState.Idle -> state.lastResult?.let { result ->
            joinWithDots(scanResultParts(result).map { (part, count) -> partText(part, count) })
        } ?: getString(R.string.library_scan_not_yet)
    }

/** The Settings row's summary: "Media/hz · 5,300 songs". */
fun Resources.librarySummary(folder: String, songs: Int): String =
    joinWithDots(listOf(folder, partText(ScanPart.SONGS, songs)))

private fun Resources.partText(part: ScanPart, count: Int): String {
    val plural = when (part) {
        ScanPart.SONGS -> R.plurals.library_songs
        ScanPart.BOOKS -> R.plurals.library_books
        ScanPart.UNREADABLE -> R.plurals.library_unreadable_files
    }
    return getQuantityString(plural, count, formatCount(count))
}
