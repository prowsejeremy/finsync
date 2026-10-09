package com.jpd.hz.ui

import com.jpd.hz.sync.SyncCounts

/**
 * The Settings Sync row's summary before it's put into words (spec "Sync row summary"): the
 * status whose label leads, then a running sync's percentage or, when idle with something
 * selected, the counts. Plain Kotlin, so each row of the spec's table is unit tested; Settings
 * formats it with the Sync card's helpers (syncRowDetail in SyncDisplayViews.kt).
 */
data class SyncRowSummary(
    val status: SyncDisplay.Status,
    /** From 0 to 100, only while a sync with a known total runs. */
    val percent: Int?,
    /** Only when no sync runs, the server is reachable and something is selected. */
    val counts: SyncCounts?
) {
    companion object {

        private const val FULL_PERCENT = 100

        fun from(display: SyncDisplay): SyncRowSummary = when (display.status) {
            SyncDisplay.Status.SYNCING ->
                SyncRowSummary(display.status, runPercent(display.runDone, display.runTotal), null)
            SyncDisplay.Status.OFFLINE, SyncDisplay.Status.SIGN_IN_AGAIN ->
                SyncRowSummary(display.status, null, null)
            else -> SyncRowSummary(display.status, null, display.counts.takeIf { it.total > 0 })
        }

        // Whole percent, rounded down, so the row reads 100% only once every item is done; null
        // until the run's total is known. Counted from the run's items, not the float progress.
        private fun runPercent(done: Int, total: Int): Int? {
            if (total <= 0) return null
            return (done.toLong() * FULL_PERCENT / total).toInt().coerceIn(0, FULL_PERCENT)
        }
    }
}
