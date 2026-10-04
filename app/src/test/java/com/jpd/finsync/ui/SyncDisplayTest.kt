package com.jpd.finsync.ui

import com.jpd.finsync.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SyncDisplayTest {

    private fun displayFor(
        syncState: SyncState?,
        syncedTracks: Int = 0,
        totalTracks: Int = 0,
        serverConnected: Boolean = true
    ): SyncDisplay = SyncDisplay.from(
        MainViewModel.UiState(
            syncState = syncState,
            trackStats = Pair(syncedTracks, totalTracks),
            serverConnected = serverConnected
        )
    )

    @Test
    fun `offline wins over a running sync and shows no error`() {
        val display = displayFor(
            SyncState(isRunning = true, totalItems = 10, downloadedItems = 4, errorMessage = "boom"),
            serverConnected = false
        )

        assertEquals(SyncDisplay.Status.OFFLINE, display.status)
        assertNull(display.errorMessage)
    }

    @Test
    fun `no sync state yet counts as idle`() {
        val display = displayFor(syncState = null)

        assertEquals(SyncDisplay.Status.NOT_SYNCED, display.status)
        assertNull(display.trackCount)
        assertProgress(0f, display.progress)
    }

    @Test
    fun `running before the total is known has no progress yet`() {
        val display = displayFor(SyncState(isRunning = true, totalItems = 0))

        assertEquals(SyncDisplay.Status.SYNCING, display.status)
        assertEquals(0, display.trackCount)
        assertNull(display.progress)
    }

    @Test
    fun `running sync shows items processed this run and their share of the run`() {
        val display = displayFor(
            SyncState(isRunning = true, totalItems = 200, downloadedItems = 50),
            syncedTracks = 30,
            totalTracks = 180
        )

        assertEquals(SyncDisplay.Status.SYNCING, display.status)
        assertEquals(50, display.trackCount)
        assertEquals(180, display.totalTracks)
        assertProgress(0.25f, display.progress)
    }

    @Test
    fun `stopped sync shows items processed before it stopped`() {
        val display = displayFor(
            SyncState(wasStopped = true, totalItems = 200, downloadedItems = 40),
            syncedTracks = 30,
            totalTracks = 180
        )

        assertEquals(SyncDisplay.Status.STOPPED, display.status)
        assertEquals(40, display.trackCount)
        assertProgress(0f, display.progress)
    }

    @Test
    fun `failed sync passes the error through and shows synced tracks`() {
        val display = displayFor(SyncState(errorMessage = "boom"), syncedTracks = 10, totalTracks = 100)

        assertEquals(SyncDisplay.Status.FAILED, display.status)
        assertEquals(10, display.trackCount)
        assertEquals("boom", display.errorMessage)
    }

    @Test
    fun `failed sync with no total shows no count`() {
        val display = displayFor(SyncState(errorMessage = "boom"))

        assertEquals(SyncDisplay.Status.FAILED, display.status)
        assertNull(display.trackCount)
    }

    @Test
    fun `completed sync is synced with full progress`() {
        val display = displayFor(SyncState(syncComplete = true), syncedTracks = 100, totalTracks = 100)

        assertEquals(SyncDisplay.Status.SYNCED, display.status)
        assertEquals(100, display.trackCount)
        assertProgress(1f, display.progress)
    }

    @Test
    fun `completed sync with no total is synced with no count`() {
        val display = displayFor(SyncState(syncComplete = true))

        assertEquals(SyncDisplay.Status.SYNCED, display.status)
        assertNull(display.trackCount)
    }

    @Test
    fun `idle with every selected track on the device is synced`() {
        val display = displayFor(SyncState(), syncedTracks = 100, totalTracks = 100)

        assertEquals(SyncDisplay.Status.SYNCED, display.status)
        assertProgress(1f, display.progress)
    }

    @Test
    fun `idle with some tracks missing is not synced`() {
        val display = displayFor(SyncState(), syncedTracks = 40, totalTracks = 100)

        assertEquals(SyncDisplay.Status.NOT_SYNCED, display.status)
        assertEquals(40, display.trackCount)
        assertProgress(0f, display.progress)
    }

    @Test
    fun `never synced with nothing selected shows no count`() {
        val display = displayFor(SyncState())

        assertEquals(SyncDisplay.Status.NOT_SYNCED, display.status)
        assertNull(display.trackCount)
        assertEquals(0, display.totalTracks)
    }

    private fun assertProgress(expected: Float, actual: Float?) {
        assertNotNull(actual)
        assertEquals(expected, actual!!, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
