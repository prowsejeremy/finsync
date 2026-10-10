package com.jpd.hz.adapter.run

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Each connection's sync state (spec "Running connections"), in place of one app-wide state.
 * [running] names the connection whose run is going, for Home's ring; a waiting run isn't it.
 */
object SyncStates {

    private val states = ConcurrentHashMap<String, MutableStateFlow<SyncState>>()
    private val _running = MutableStateFlow<String?>(null)

    val running: StateFlow<String?> = _running.asStateFlow()

    fun of(connectionId: String): StateFlow<SyncState> = flowOf(connectionId).asStateFlow()

    fun set(connectionId: String, state: SyncState) {
        flowOf(connectionId).value = state
        when {
            state.isRunning && !state.waiting -> _running.value = connectionId
            else -> _running.compareAndSet(connectionId, null)
        }
    }

    /** Queued behind another run. */
    fun setWaiting(connectionId: String) =
        set(connectionId, SyncState(isRunning = true, waiting = true))

    /** Stopped by the user: the counts it reached stay, as before the harness. */
    fun setStopped(connectionId: String) {
        val current = flowOf(connectionId).value
        set(
            connectionId,
            SyncState(
                totalItems = current.totalItems,
                downloadedItems = current.downloadedItems,
                isRunning = false,
                wasStopped = true
            )
        )
    }

    /** Taken off the queue before it started: back to idle. */
    fun setIdle(connectionId: String) = set(connectionId, SyncState())

    private fun flowOf(connectionId: String): MutableStateFlow<SyncState> =
        states.getOrPut(connectionId) { MutableStateFlow(SyncState()) }
}
