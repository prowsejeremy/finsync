package com.jpd.finsync.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLibraryStateTest {

    @Test
    fun `a filled catalogue is ready whatever the refresh status`() {
        assertEquals(
            HomeLibraryState.Ready(3),
            homeLibraryStateOf(catalogueEmpty = false, albumCount = 3, refresh = RefreshStatus.FAILED)
        )
    }

    @Test
    fun `an empty catalogue is building until the refresh finishes`() {
        assertEquals(
            HomeLibraryState.Building,
            homeLibraryStateOf(catalogueEmpty = true, albumCount = 0, refresh = RefreshStatus.RUNNING)
        )
        assertEquals(
            HomeLibraryState.Building,
            homeLibraryStateOf(catalogueEmpty = true, albumCount = 0, refresh = RefreshStatus.IDLE)
        )
    }

    @Test
    fun `an empty catalogue after a failed refresh asks to connect`() {
        assertEquals(
            HomeLibraryState.Failed,
            homeLibraryStateOf(catalogueEmpty = true, albumCount = 0, refresh = RefreshStatus.FAILED)
        )
    }

    @Test
    fun `an empty catalogue after a successful refresh shows zero albums`() {
        assertEquals(
            HomeLibraryState.Ready(0),
            homeLibraryStateOf(catalogueEmpty = true, albumCount = 0, refresh = RefreshStatus.DONE)
        )
    }

    @Test
    fun `ready carries every category count`() {
        assertEquals(
            HomeLibraryState.Ready(42, 18, 9, 512),
            homeLibraryStateOf(
                catalogueEmpty = false,
                albumCount = 42,
                refresh = RefreshStatus.DONE,
                albumArtistCount = 18,
                genreCount = 9,
                songCount = 512
            )
        )
    }

    @Test
    fun `nothing is visible only with no albums and no songs`() {
        assertTrue(HomeLibraryState.Ready(0).nothingVisible)
        assertFalse(HomeLibraryState.Ready(0, songCount = 3).nothingVisible)
        assertFalse(HomeLibraryState.Ready(2, songCount = 20).nothingVisible)
    }

    @Test
    fun `ready carries the playlist count`() {
        assertEquals(
            HomeLibraryState.Ready(42, 18, 9, 512, playlistCount = 6),
            homeLibraryStateOf(
                catalogueEmpty = false,
                albumCount = 42,
                refresh = RefreshStatus.DONE,
                albumArtistCount = 18,
                genreCount = 9,
                songCount = 512,
                playlistCount = 6
            )
        )
    }

    @Test
    fun `books alone are something to show, and ready carries the book count`() {
        assertFalse(HomeLibraryState.Ready(0, bookCount = 2).nothingVisible)
        assertEquals(
            HomeLibraryState.Ready(0, bookCount = 2),
            homeLibraryStateOf(
                catalogueEmpty = false,
                albumCount = 0,
                refresh = RefreshStatus.DONE,
                bookCount = 2
            )
        )
    }
}
