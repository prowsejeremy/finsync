package com.jpd.finsync.ui

import org.junit.Assert.assertEquals
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
}
