package com.jpd.hz.ui

import com.jpd.hz.model.ServerConfig
import com.jpd.hz.model.SyncState
import com.jpd.hz.sync.SyncCounts
import org.junit.Assert.assertEquals
import org.junit.Test

/** Jellyfin's row in Settings → Adapters: signed out, or the Sync card's state (T4). */
class AdapterStatusTest {

    private val config = ServerConfig(
        serverUrl = "http://kurage", serverId = "server-1", serverName = "kurage",
        userId = "user", username = "me", accessToken = "token"
    )
    private val syncing = MainViewModel.UiState(
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
            adapterStatusOf(config, syncing)
        )
    }
}
