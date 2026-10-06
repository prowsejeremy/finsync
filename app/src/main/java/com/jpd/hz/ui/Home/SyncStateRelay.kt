package com.jpd.hz.ui

import com.jpd.hz.model.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/** A sync that completed, stopped or failed, so what's on the device may have changed. */
val SyncState.hasEnded: Boolean
    get() = !isRunning && (syncComplete || wasStopped || errorMessage != null)

/**
 * Passes each sync state to [post], refreshing the counts first when a sync has ended, so the
 * Sync card never shows the final state with the old counts (spec "MainViewModel").
 *
 * collectLatest cancels a refresh still running when a newer state arrives, before the older
 * state is posted; the newer one is posted instead. So the newest state always shows, and an
 * older one can't land after it.
 */
suspend fun relaySyncStates(
    states: Flow<SyncState>,
    refreshCounts: suspend () -> Unit,
    post: (SyncState) -> Unit
) {
    states.collectLatest { state ->
        if (state.hasEnded) refreshCounts()
        post(state)
    }
}
