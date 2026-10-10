package com.jpd.hz.ui

import com.jpd.hz.adapter.run.SyncCounts
import com.jpd.hz.adapter.run.SyncState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * One test per row of the spec's "Sync row summary" table, through SyncDisplay.from() as
 * Settings uses it, plus the harness's Waiting and Nothing chosen (adapter harness spec, H4 and
 * H9.4).
 */
class SyncRowSummaryTest {

    // 1,280 of 1,400 songs · 0 of 2 books: something is chosen and not everything is synced.
    private val someCounts = SyncCounts(1_280, 1_400, 0, 2)

    private fun summaryFor(
        syncState: SyncState?,
        counts: SyncCounts = someCounts,
        serverConnected: Boolean = true,
        signInRefused: Boolean = false
    ): SyncRowSummary = SyncRowSummary.from(
        SyncDisplay.from(
            ConnectionUiState(
                syncState = syncState,
                syncCounts = counts,
                serverConnected = serverConnected,
                signInRefused = signInRefused
            )
        )
    )

    @Test
    fun `a sync with a known total shows its label and percentage`() {
        val summary =
            summaryFor(SyncState(isRunning = true, totalItems = 200, downloadedItems = 90))

        assertEquals(SyncRowSummary(SyncDisplay.Status.SYNCING, 45, null), summary)
    }

    @Test
    fun `the percentage rounds down, so it reads 100 only when every item is done`() {
        val almost =
            summaryFor(SyncState(isRunning = true, totalItems = 1_000, downloadedItems = 999))
        val done =
            summaryFor(SyncState(isRunning = true, totalItems = 1_000, downloadedItems = 1_000))

        assertEquals(99, almost.percent)
        assertEquals(100, done.percent)
    }

    @Test
    fun `a sync with no total yet shows only its label`() {
        val summary = summaryFor(SyncState(isRunning = true, totalItems = 0))

        assertEquals(SyncRowSummary(SyncDisplay.Status.SYNCING, null, null), summary)
    }

    @Test
    fun `a waiting run shows only its label, with no percentage or counts`() {
        val summary = summaryFor(
            SyncState(isRunning = true, waiting = true, totalItems = 200, downloadedItems = 90)
        )

        assertEquals(SyncRowSummary(SyncDisplay.Status.WAITING, null, null), summary)
    }

    @Test
    fun `offline shows only its label, even with items chosen`() {
        val summary = summaryFor(
            SyncState(isRunning = true, totalItems = 10, downloadedItems = 4),
            serverConnected = false
        )

        assertEquals(SyncRowSummary(SyncDisplay.Status.OFFLINE, null, null), summary)
    }

    @Test
    fun `a refused sign-in shows only its label, even with items chosen`() {
        val summary = summaryFor(SyncState(syncComplete = true), signInRefused = true)

        assertEquals(SyncRowSummary(SyncDisplay.Status.SIGN_IN_AGAIN, null, null), summary)
    }

    @Test
    fun `any other status shows its label and the counts when something is chosen`() {
        val allSynced = SyncCounts(1_400, 1_400, 0, 0)
        val synced = summaryFor(SyncState(syncComplete = true), counts = allSynced)
        val notSynced = summaryFor(SyncState())
        val stopped = summaryFor(SyncState(wasStopped = true))
        val failed = summaryFor(SyncState(errorMessage = "boom"))
        val incomplete = summaryFor(SyncState(syncComplete = true, failedItems = 2))

        assertEquals(SyncRowSummary(SyncDisplay.Status.SYNCED, null, allSynced), synced)
        assertEquals(SyncRowSummary(SyncDisplay.Status.NOT_SYNCED, null, someCounts), notSynced)
        assertEquals(SyncRowSummary(SyncDisplay.Status.STOPPED, null, someCounts), stopped)
        assertEquals(SyncRowSummary(SyncDisplay.Status.FAILED, null, someCounts), failed)
        assertEquals(SyncRowSummary(SyncDisplay.Status.INCOMPLETE, null, someCounts), incomplete)
    }

    @Test
    fun `any other status shows only its label when nothing is chosen`() {
        val synced = summaryFor(SyncState(syncComplete = true), counts = SyncCounts.NONE)
        val nothingChosen = summaryFor(
            SyncState(errorMessage = "Choose what to sync first.", nothingChosen = true),
            counts = SyncCounts.NONE
        )

        assertEquals(SyncRowSummary(SyncDisplay.Status.SYNCED, null, null), synced)
        assertEquals(SyncRowSummary(SyncDisplay.Status.NOTHING_CHOSEN, null, null), nothingChosen)
    }
}
