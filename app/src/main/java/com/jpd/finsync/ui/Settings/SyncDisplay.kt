package com.jpd.finsync.ui

import com.jpd.finsync.model.SyncState

/**
 * What the Sync card and the Sync Status screen show for the current sync and server state.
 * It has no Android dependencies, so the rules can be unit tested on the JVM.
 */
data class SyncDisplay(
    val status: Status,
    /** Rendered as "—" when null. */
    val trackCount: Int?,
    /** The selected albums' track total. Zero means there's no total to show. */
    val totalTracks: Int,
    /** From 0 to 1, or null while a sync is running but its total isn't known yet. */
    val progress: Float?,
    val errorMessage: String?,
    /** Items the last sync couldn't sync; above zero only when the status is INCOMPLETE. */
    val failedItems: Int = 0
) {
    enum class Status { OFFLINE, SYNCING, STOPPED, FAILED, INCOMPLETE, SYNCED, NOT_SYNCED }

    companion object {

        fun from(state: MainViewModel.UiState): SyncDisplay {
            val (syncedTracks, totalTracks) = state.trackStats
            if (!state.serverConnected) {
                // Offline overrides every sync state, and the offline screen shows no error.
                return SyncDisplay(Status.OFFLINE, null, totalTracks, 0f, null)
            }

            // Null until SyncEngine's first emission; treat that as idle.
            val sync = state.syncState ?: SyncState()
            val idleCount = if (totalTracks > 0) syncedTracks else null

            fun display(status: Status, trackCount: Int?, progress: Float?) =
                SyncDisplay(status, trackCount, totalTracks, progress, sync.errorMessage)

            return when {
                sync.isRunning -> display(Status.SYNCING, sync.downloadedItems, runningProgress(sync))
                sync.wasStopped -> display(Status.STOPPED, sync.downloadedItems, 0f)
                sync.errorMessage != null -> display(Status.FAILED, idleCount, 0f)
                // Before SYNCED, which a full device would otherwise win.
                sync.failedItems > 0 ->
                    display(Status.INCOMPLETE, idleCount, 0f).copy(failedItems = sync.failedItems)
                sync.syncComplete || (totalTracks > 0 && syncedTracks >= totalTracks) ->
                    display(Status.SYNCED, idleCount, 1f)
                else -> display(Status.NOT_SYNCED, idleCount, 0f)
            }
        }

        private fun runningProgress(sync: SyncState): Float? =
            if (sync.totalItems > 0) sync.downloadedItems.toFloat() / sync.totalItems else null
    }
}
