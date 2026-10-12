package com.jpd.hz.ui

import com.jpd.hz.library.scan.ScanResult
import com.jpd.hz.library.scan.ScanState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLibraryStateTest {

    private val done = ScanState.Idle(ScanResult(0, 0, 0, 0, 1L))

    @Test
    fun `a library with music is ready whatever the scan is doing`() {
        listOf(ScanState.Idle(null), ScanState.Scanning(1, 2), done).forEach { scan ->
            assertEquals(
                HomeLibraryState.Ready(3),
                homeLibraryStateOf(libraryEmpty = false, scan = scan, albumCount = 3)
            )
        }
    }

    @Test
    fun `a library with music keeps its cards when the folder can't be read`() {
        assertEquals(
            HomeLibraryState.Ready(3, scanFailed = true),
            homeLibraryStateOf(false, ScanState.Failed("/storage/emulated/0/Media/hz"), 3)
        )
    }

    @Test
    fun `an empty library is building until a scan finishes`() {
        assertEquals(HomeLibraryState.Building, homeLibraryStateOf(true, ScanState.Idle(null), 0))
        assertEquals(
            HomeLibraryState.Building,
            homeLibraryStateOf(true, ScanState.Scanning(0, 0), 0)
        )
    }

    @Test
    fun `an empty library whose folder can't be read has failed`() {
        assertEquals(
            HomeLibraryState.Failed,
            homeLibraryStateOf(true, ScanState.Failed("/storage/emulated/0/Media/hz"), 0)
        )
    }

    @Test
    fun `an empty library after a scan shows the empty state`() {
        val state = homeLibraryStateOf(true, done, 0) as HomeLibraryState.Ready
        assertTrue(state.isEmpty)
        assertFalse(state.scanFailed)
    }

    @Test
    fun `counts carry through`() {
        assertEquals(
            HomeLibraryState.Ready(1, 2, 3, 4, 5, 6),
            homeLibraryStateOf(
                libraryEmpty = false,
                scan = done,
                albumCount = 1,
                albumArtistCount = 2,
                genreCount = 3,
                songCount = 4,
                playlistCount = 5,
                bookCount = 6
            )
        )
    }

    @Test
    fun `a library is empty with no albums, songs or books, whatever the playlists`() {
        assertTrue(HomeLibraryState.Ready(0, playlistCount = 2).isEmpty)
        assertFalse(HomeLibraryState.Ready(0, songCount = 1).isEmpty)
        assertFalse(HomeLibraryState.Ready(0, bookCount = 1).isEmpty)
    }
}
