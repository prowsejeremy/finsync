package com.jpd.hz.ui

import com.jpd.hz.adapter.run.SyncState
import com.jpd.hz.adapter.run.SyncCounts

/**
 * What the Sync card and Home's sync ring show for the current sync and server state. It has no
 * Android dependencies, so the rules can be unit tested on the JVM.
 */
data class SyncDisplay(
    val status: Status,
    /** The selected songs and books, and how many are on the device. */
    val counts: SyncCounts,
    /** Items this run has processed, and its total; both 0 unless the status is SYNCING. */
    val runDone: Int,
    val runTotal: Int,
    /** From 0 to 1, or null while a sync is running but its total isn't known yet. */
    val progress: Float?,
    val errorMessage: String?,
    /** Items the last sync couldn't sync; above zero only when the status is INCOMPLETE. */
    val failedItems: Int = 0,
    /**
     * Files the last sync couldn't tag (T2). Above zero only after a finished sync: INCOMPLETE,
     * SYNCED or NOT_SYNCED. It never changes the status.
     */
    val untaggedFiles: Int = 0
) {
    enum class Status {
        SIGN_IN_AGAIN, OFFLINE, WAITING, SYNCING, STOPPED, NOTHING_CHOSEN, FAILED, INCOMPLETE,
        SYNCED, NOT_SYNCED
    }

    companion object {

        private val FINISHED = setOf(Status.INCOMPLETE, Status.SYNCED, Status.NOT_SYNCED)

        fun from(state: ConnectionUiState): SyncDisplay {
            val counts = state.syncCounts
            if (state.signInRefused) {
                // A refused sign-in overrides offline and every sync state, and shows no error
                // (spec "Sign-in health", decision 4).
                return SyncDisplay(Status.SIGN_IN_AGAIN, counts, 0, 0, 0f, null)
            }
            if (!state.serverConnected) {
                // Offline overrides every sync state, and shows no error.
                return SyncDisplay(Status.OFFLINE, counts, 0, 0, 0f, null)
            }

            // Null until the connection's first state; treat that as idle.
            val sync = state.syncState ?: SyncState()

            fun display(status: Status, progress: Float?) =
                SyncDisplay(status, counts, 0, 0, progress, sync.errorMessage)

            val shown = when {
                // Queued behind another connection's run (adapter harness spec, H9.4).
                sync.waiting -> display(Status.WAITING, null)
                sync.isRunning -> display(Status.SYNCING, runningProgress(sync))
                    .copy(runDone = sync.downloadedItems, runTotal = sync.totalItems)
                sync.wasStopped -> display(Status.STOPPED, 0f)
                // Not a failure: the run didn't start (adapter harness spec, H4).
                sync.nothingChosen -> display(Status.NOTHING_CHOSEN, 0f).copy(errorMessage = null)
                sync.errorMessage != null -> display(Status.FAILED, 0f)
                // Before SYNCED, which a full device would otherwise win.
                sync.failedItems > 0 ->
                    display(Status.INCOMPLETE, 0f).copy(failedItems = sync.failedItems)
                // With anything selected the counts decide, so a new choice shows as not synced
                // even after a completed sync (spec "SyncDisplay", rule 6).
                counts.total > 0 && counts.allSynced -> display(Status.SYNCED, 1f)
                counts.total > 0 -> display(Status.NOT_SYNCED, 0f)
                sync.syncComplete -> display(Status.SYNCED, 1f)
                else -> display(Status.NOT_SYNCED, 0f)
            }
            // The tagging line follows whatever a finished sync shows (T2 spec).
            if (shown.status !in FINISHED) return shown
            return shown.copy(untaggedFiles = sync.untaggedFiles)
        }

        private fun runningProgress(sync: SyncState): Float? =
            if (sync.totalItems > 0) sync.downloadedItems.toFloat() / sync.totalItems else null
    }
}
