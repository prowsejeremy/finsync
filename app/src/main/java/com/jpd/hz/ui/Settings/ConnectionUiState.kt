package com.jpd.hz.ui

import com.jpd.hz.adapter.run.SyncCounts
import com.jpd.hz.adapter.run.SyncState

/**
 * What one connection's Sync card, Adapters row and Settings row are drawn from (spec "Running
 * connections"); [SyncDisplay.from] turns it into a status. Plain Kotlin, so the rules are tested
 * on the JVM.
 */
data class ConnectionUiState(
    val syncState: SyncState? = null,
    val syncCounts: SyncCounts = SyncCounts.NONE,
    val serverConnected: Boolean = true,
    /** The source refuses the saved sign-in (spec "Sign-in health", decision 2). */
    val signInRefused: Boolean = false
)
