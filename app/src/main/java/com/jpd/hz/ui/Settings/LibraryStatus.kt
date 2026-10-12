package com.jpd.hz.ui

import com.jpd.hz.library.scan.ScanResult

/** One part of the scan card's result line, such as "5,300 songs". */
enum class ScanPart { SONGS, BOOKS, UNREADABLE }

/**
 * The scan card's last result (spec "Settings → Library"): "5,300 songs · 12 books · 3 files
 * couldn't be read". Songs always show; books and unreadable files only when there are some.
 */
fun scanResultParts(result: ScanResult): List<Pair<ScanPart, Int>> = buildList {
    add(ScanPart.SONGS to result.songs)
    if (result.books > 0) add(ScanPart.BOOKS to result.books)
    if (result.unreadable > 0) add(ScanPart.UNREADABLE to result.unreadable)
}
