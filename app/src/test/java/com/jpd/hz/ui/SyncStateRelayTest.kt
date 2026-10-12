package com.jpd.hz.ui

import com.jpd.hz.adapter.run.SyncState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncStateRelayTest {

    private fun label(state: SyncState) = if (state.isRunning) "running" else "ended"

    @Test
    fun `completed, stopped and failed syncs have ended, running and idle ones haven't`() {
        assertTrue(SyncState(syncComplete = true).hasEnded)
        assertTrue(SyncState(wasStopped = true).hasEnded)
        assertTrue(SyncState(errorMessage = "boom").hasEnded)
        assertFalse(SyncState(isRunning = true).hasEnded)
        assertFalse(SyncState().hasEnded)
    }

    @Test
    fun `an ended sync refreshes the counts before its state is posted`() {
        val events = mutableListOf<String>()

        runBlocking {
            relaySyncStates(
                flowOf(SyncState(isRunning = true), SyncState(syncComplete = true)),
                refreshCounts = { events += "counts" },
                post = { events += label(it) }
            )
        }

        assertEquals(listOf("running", "counts", "ended"), events)
    }

    @Test
    fun `a newer state during the refresh is posted instead of the ended one`() {
        val events = mutableListOf<String>()
        val refreshStarted = CompletableDeferred<Unit>()
        val runningPosted = CompletableDeferred<Unit>()
        val states = MutableStateFlow(SyncState(syncComplete = true))

        runBlocking {
            val relay = launch {
                relaySyncStates(
                    states,
                    refreshCounts = {
                        events += "counts"
                        refreshStarted.complete(Unit)
                        // Holds the refresh open until the newer state cancels it.
                        CompletableDeferred<Unit>().await()
                    },
                    post = { state ->
                        events += label(state)
                        if (state.isRunning) runningPosted.complete(Unit)
                    }
                )
            }
            withTimeout(TIMEOUT_MS) { refreshStarted.await() }
            states.value = SyncState(isRunning = true)
            withTimeout(TIMEOUT_MS) { runningPosted.await() }
            relay.cancel()
        }

        assertEquals(listOf("counts", "running"), events)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
