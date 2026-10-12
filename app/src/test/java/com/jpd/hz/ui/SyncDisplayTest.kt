package com.jpd.hz.ui

import com.jpd.hz.adapter.run.SyncCounts
import com.jpd.hz.adapter.run.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

// A run that didn't start because nothing is chosen (SyncRun). A message, if one came with it,
// would still not show as an error.
private val NOTHING_CHOSEN_STATE =
    SyncState(errorMessage = "Choose what to sync first.", nothingChosen = true)

/**
 * SyncDisplay's rules, from one connection's state. The 3b refinements replaced the track total
 * with SyncCounts and a running sync's own item counts, and the counts decide SYNCED whenever
 * anything is chosen; the harness added Waiting and Nothing chosen (adapter harness spec, H4 and
 * H9.4).
 */
class SyncDisplayTest {

    private fun displayFor(
        syncState: SyncState?,
        counts: SyncCounts = SyncCounts.NONE,
        serverConnected: Boolean = true,
        signInRefused: Boolean = false
    ): SyncDisplay = SyncDisplay.from(
        ConnectionUiState(
            syncState = syncState,
            syncCounts = counts,
            serverConnected = serverConnected,
            signInRefused = signInRefused
        )
    )

    @Test
    fun `offline wins over a running sync and shows no error`() {
        val running = SyncState(
            isRunning = true,
            totalItems = 10,
            downloadedItems = 4,
            errorMessage = "boom"
        )

        val display = displayFor(running, serverConnected = false)

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
    fun `a run queued behind another is waiting, not syncing, with no progress yet`() {
        val display = displayFor(
            SyncState(isRunning = true, waiting = true),
            counts = SyncCounts(30, 180, 0, 0)
        )

        assertEquals(SyncDisplay.Status.WAITING, display.status)
        assertEquals(0, display.runDone)
        assertEquals(0, display.runTotal)
        assertNull(display.progress)
        assertNull(display.errorMessage)
    }

    @Test
    fun `offline and a refused sign-in still win over waiting`() {
        val waiting = SyncState(isRunning = true, waiting = true)

        assertEquals(
            SyncDisplay.Status.OFFLINE,
            displayFor(waiting, serverConnected = false).status
        )
        assertEquals(
            SyncDisplay.Status.SIGN_IN_AGAIN,
            displayFor(waiting, signInRefused = true).status
        )
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
    fun `nothing chosen comes after stopped and before failed`() {
        assertEquals(SyncDisplay.Status.NOTHING_CHOSEN, displayFor(NOTHING_CHOSEN_STATE).status)
        assertEquals(
            SyncDisplay.Status.STOPPED,
            displayFor(NOTHING_CHOSEN_STATE.copy(wasStopped = true)).status
        )
        assertProgress(0f, displayFor(NOTHING_CHOSEN_STATE).progress)
    }

    @Test
    fun `nothing chosen shows no error, even with a message`() {
        val display = displayFor(NOTHING_CHOSEN_STATE)

        assertEquals(SyncDisplay.Status.NOTHING_CHOSEN, display.status)
        assertNull(display.errorMessage)
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
    fun `failed sync with nothing chosen has no counts`() {
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
    fun `completed sync with nothing chosen is synced`() {
        val display = displayFor(SyncState(syncComplete = true))

        assertEquals(SyncDisplay.Status.SYNCED, display.status)
        assertEquals(0, display.counts.total)
    }

    @Test
    fun `completed sync then a newly chosen playlist or book is not synced`() {
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
    fun `idle with every chosen item on the device is synced`() {
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
    fun `never synced with nothing chosen is not synced`() {
        val display = displayFor(SyncState())

        assertEquals(SyncDisplay.Status.NOT_SYNCED, display.status)
        assertEquals(0, display.counts.total)
    }

    @Test
    fun `a completed sync with failed items is incomplete and counts them`() {
        // Every chosen item can be on the device while a playlist's fetch failed.
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
