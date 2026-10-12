package com.jpd.hz.ui

import com.jpd.hz.adapter.run.SyncCounts
import com.jpd.hz.adapter.run.SyncState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tagging line (T2): "2 files couldn't be tagged." follows a finished sync's line, and never
 * changes its status. Waiting and Nothing chosen aren't finished syncs (adapter harness spec, H4
 * and H9.4).
 */
class SyncDisplayTaggingTest {

    private fun displayFor(
        syncState: SyncState,
        counts: SyncCounts = SyncCounts(10, 10, 0, 0),
        serverConnected: Boolean = true
    ): SyncDisplay = SyncDisplay.from(
        ConnectionUiState(
            syncState = syncState,
            syncCounts = counts,
            serverConnected = serverConnected
        )
    )

    private val finished = SyncState(syncComplete = true, untaggedFiles = 2)

    @Test
    fun `a finished sync carries its untagged files and keeps its status`() {
        val synced = displayFor(finished)
        assertEquals(SyncDisplay.Status.SYNCED, synced.status)
        assertEquals(2, synced.untaggedFiles)

        val incomplete = displayFor(finished.copy(failedItems = 1))
        assertEquals(SyncDisplay.Status.INCOMPLETE, incomplete.status)
        assertEquals(1, incomplete.failedItems)
        assertEquals(2, incomplete.untaggedFiles)

        val notSynced = displayFor(finished, counts = SyncCounts(8, 10, 0, 0))
        assertEquals(SyncDisplay.Status.NOT_SYNCED, notSynced.status)
        assertEquals(2, notSynced.untaggedFiles)
    }

    @Test
    fun `a running, stopped, failed or offline display has no tagging line`() {
        assertEquals(0, displayFor(finished.copy(isRunning = true)).untaggedFiles)
        assertEquals(0, displayFor(finished.copy(wasStopped = true)).untaggedFiles)
        assertEquals(0, displayFor(finished.copy(errorMessage = "boom")).untaggedFiles)
        assertEquals(0, displayFor(finished, serverConnected = false).untaggedFiles)
    }

    @Test
    fun `a waiting or nothing-chosen display has no tagging line`() {
        val waiting = displayFor(finished.copy(isRunning = true, waiting = true))
        val nothingChosen = displayFor(finished.copy(syncComplete = false, nothingChosen = true))

        assertEquals(SyncDisplay.Status.WAITING, waiting.status)
        assertEquals(0, waiting.untaggedFiles)
        assertEquals(SyncDisplay.Status.NOTHING_CHOSEN, nothingChosen.status)
        assertEquals(0, nothingChosen.untaggedFiles)
    }
}
