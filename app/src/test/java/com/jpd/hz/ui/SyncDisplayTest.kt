package com.jpd.hz.ui

import com.jpd.hz.model.SyncState
import com.jpd.hz.sync.SyncCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * SyncDisplay's rules. The 3b refinements replaced the track total with SyncCounts and a running
 * sync's own item counts, and the counts now decide SYNCED whenever anything is selected.
 */
class SyncDisplayTest {

    private fun displayFor(
        syncState: SyncState?,
        counts: SyncCounts = SyncCounts.NONE,
        serverConnected: Boolean = true,
        signInRefused: Boolean = false
    ): SyncDisplay = SyncDisplay.from(
        MainViewModel.UiState(
            syncState = syncState,
            syncCounts = counts,
            serverConnected = serverConnected,
            signInRefused = signInRefused
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
    fun `a refused sign-in wins over offline and a running sync, with no error`() {
        val running = SyncState(
            isRunning = true,
            totalItems = 10,
            downloadedItems = 4,
            errorMessage = "boom"
        )

        val display = displayFor(
            running,
            serverConnected = false,
            signInRefused = true
        )

        assertEquals(SyncDisplay.Status.SIGN_IN_AGAIN, display.status)
        assertNull(display.errorMessage)
    }

    @Test
    fun `a refused sign-in wins over the failed sync it caused`() {
        val display = displayFor(
            SyncState(errorMessage = "Failed to fetch audio items: 401"),
            signInRefused = true
        )

        assertEquals(SyncDisplay.Status.SIGN_IN_AGAIN, display.status)
        assertNull(display.errorMessage)
    }

    @Test
    fun `no sync state yet counts as idle`() {
        val display = displayFor(syncState = null)

        assertEquals(SyncDisplay.Status.NOT_SYNCED, display.status)
        assertEquals(SyncCounts.NONE, display.counts)
        assertProgress(0f, display.progress)
    }

    @Test
    fun `running before the total is known has no progress yet`() {
        val display = displayFor(SyncState(isRunning = true, totalItems = 0))

        assertEquals(SyncDisplay.Status.SYNCING, display.status)
        assertEquals(0, display.runTotal)
        assertNull(display.progress)
    }

    @Test
    fun `running sync carries this run's items and their share of the run`() {
        val display = displayFor(
            SyncState(isRunning = true, totalItems = 200, downloadedItems = 50),
            counts = SyncCounts(30, 180, 0, 0)
        )

        assertEquals(SyncDisplay.Status.SYNCING, display.status)
        assertEquals(50, display.runDone)
        assertEquals(200, display.runTotal)
        assertProgress(0.25f, display.progress)
    }

    @Test
    fun `stopped sync keeps the counts and carries no run`() {
        val counts = SyncCounts(30, 180, 0, 0)
        val display = displayFor(
            SyncState(wasStopped = true, totalItems = 200, downloadedItems = 40),
            counts = counts
        )

        assertEquals(SyncDisplay.Status.STOPPED, display.status)
        assertEquals(counts, display.counts)
        assertEquals(0, display.runDone)
        assertProgress(0f, display.progress)
    }

    @Test
    fun `failed sync passes the error through and keeps the counts`() {
        val counts = SyncCounts(10, 100, 0, 0)
        val display = displayFor(SyncState(errorMessage = "boom"), counts = counts)

        assertEquals(SyncDisplay.Status.FAILED, display.status)
        assertEquals(counts, display.counts)
        assertEquals("boom", display.errorMessage)
    }

    @Test
    fun `failed sync with nothing selected has no counts`() {
        val display = displayFor(SyncState(errorMessage = "boom"))

        assertEquals(SyncDisplay.Status.FAILED, display.status)
        assertEquals(0, display.counts.total)
    }

    @Test
    fun `completed sync with everything on the device is synced with full progress`() {
        val display = displayFor(
            SyncState(syncComplete = true),
            counts = SyncCounts(100, 100, 2, 2)
        )

        assertEquals(SyncDisplay.Status.SYNCED, display.status)
        assertProgress(1f, display.progress)
    }

    @Test
    fun `completed sync with nothing selected is synced`() {
        val display = displayFor(SyncState(syncComplete = true))

        assertEquals(SyncDisplay.Status.SYNCED, display.status)
        assertEquals(0, display.counts.total)
    }

    @Test
    fun `completed sync then a newly selected playlist or book is not synced`() {
        val newPlaylist = displayFor(
            SyncState(syncComplete = true),
            counts = SyncCounts(100, 120, 0, 0)
        )
        val newBook = displayFor(
            SyncState(syncComplete = true),
            counts = SyncCounts(100, 100, 0, 1)
        )

        assertEquals(SyncDisplay.Status.NOT_SYNCED, newPlaylist.status)
        assertEquals(SyncDisplay.Status.NOT_SYNCED, newBook.status)
        assertProgress(0f, newBook.progress)
    }

    @Test
    fun `idle with every selected item on the device is synced`() {
        val display = displayFor(SyncState(), counts = SyncCounts(100, 100, 1, 1))

        assertEquals(SyncDisplay.Status.SYNCED, display.status)
        assertProgress(1f, display.progress)
    }

    @Test
    fun `idle with some items missing is not synced`() {
        val display = displayFor(SyncState(), counts = SyncCounts(40, 100, 0, 0))

        assertEquals(SyncDisplay.Status.NOT_SYNCED, display.status)
        assertEquals(40, display.counts.songsSynced)
        assertProgress(0f, display.progress)
    }

    @Test
    fun `never synced with nothing selected is not synced`() {
        val display = displayFor(SyncState())

        assertEquals(SyncDisplay.Status.NOT_SYNCED, display.status)
        assertEquals(0, display.counts.total)
    }

    @Test
    fun `a completed sync with failed items is incomplete and counts them`() {
        // Every selected item can be on the device while a playlist's fetch failed.
        val display = displayFor(
            SyncState(syncComplete = true, failedItems = 2),
            counts = SyncCounts(100, 100, 0, 0)
        )

        assertEquals(SyncDisplay.Status.INCOMPLETE, display.status)
        assertEquals(2, display.failedItems)
    }

    private fun assertProgress(expected: Float, actual: Float?) {
        assertNotNull(actual)
        assertEquals(expected, actual!!, DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
