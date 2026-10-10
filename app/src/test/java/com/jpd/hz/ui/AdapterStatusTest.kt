package com.jpd.hz.ui

import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.run.SyncCounts
import com.jpd.hz.adapter.run.SyncState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A platform's row in Settings → Adapters: signed out, or its connection's Sync card state
 * (adapter harness spec, "Screens"; T4).
 */
class AdapterStatusTest {

    private val kurage = Connection(platform = "jellyfin", sourceId = "server-1", name = "kurage")
    private val syncing = ConnectionUiState(
        syncState = SyncState(isRunning = true, totalItems = 4, downloadedItems = 1),
        syncCounts = SyncCounts.NONE
    )

    @Test
    fun `signed out, the row says so whatever the sync state`() {
        assertEquals(AdapterStatus.SignedOut, adapterStatusOf(null, syncing))
    }

    @Test
    fun `signed in, the row shows what the Sync card shows`() {
        assertEquals(
            AdapterStatus.SignedIn(SyncDisplay.from(syncing)),
            adapterStatusOf(kurage, syncing)
        )
    }
}
