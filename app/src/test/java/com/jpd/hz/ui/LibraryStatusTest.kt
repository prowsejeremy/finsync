package com.jpd.hz.ui

import com.jpd.hz.library.scan.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStatusTest {

    @Test
    fun songsAlwaysShowAndTheRestOnlyWhenThereAreSome() {
        assertEquals(
            listOf(ScanPart.SONGS to 5_300, ScanPart.BOOKS to 12, ScanPart.UNREADABLE to 3),
            scanResultParts(ScanResult(5_300, 12, 4, 3, 0L))
        )
        assertEquals(
            listOf(ScanPart.SONGS to 0),
            scanResultParts(ScanResult(0, 0, 0, 0, 0L))
        )
        assertEquals(
            listOf(ScanPart.SONGS to 1, ScanPart.UNREADABLE to 1),
            scanResultParts(ScanResult(1, 0, 0, 1, 0L))
        )
    }
}
