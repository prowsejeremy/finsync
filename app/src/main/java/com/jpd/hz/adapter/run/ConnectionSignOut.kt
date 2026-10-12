package com.jpd.hz.adapter.run

import android.content.Context
import android.util.Log
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.adapter.folders.FolderSetup
import com.jpd.hz.adapter.service.AutoSync
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ConnectionSignOut"

/**
 * Signing a connection out (spec "Running connections"; T4 B1, B2). It clears the connection's
 * sign-in, its catalogue and its schedule.
 *
 * It keeps the files, the folder entry, the sync records and the choices, so signing in again
 * downloads and re-tags nothing and finds the same choices. The player's queue, book progress and
 * playback aren't the adapter's, so they stay too (D10).
 */
class ConnectionSignOut internal constructor(
    private val connectionId: String,
    private val catalogue: Catalogue,
    private val clearSignIn: suspend () -> Unit,
    private val turnOffSchedule: () -> Unit,
    private val signedIn: () -> Boolean
) {

    constructor(context: Context, connection: Connection) : this(
        connection.id,
        Catalogue(context),
        { Platforms.find(connection.platform)?.clearSignIn(connection) },
        { AutoSync(context).turnOff(connection.id) },
        { Platforms.connection(connection.id) != null }
    )

    /**
     * In an order that leaves nothing half-done:
     * 1. The sign-in goes first, so no run, catalogue refresh or scheduled run starts again.
     * 2. The schedule goes, which also stops a scheduled run that's going.
     * 3. It waits for [FolderSetup.lock], stopping this connection's run if it goes meanwhile
     *    with [stopSync], so its catalogue write can't land after the clear. Another
     *    connection's run finishes first.
     * 4. The catalogue goes, unless the user has signed in again meanwhile.
     * Nothing in it can be cancelled. Cut short after step 1, the next sign-in's refresh replaces
     * the catalogue.
     */
    suspend fun run(syncStates: Flow<SyncState>, stopSync: () -> Unit) {
        withContext(NonCancellable) {
            clearSignIn()
            turnOffSchedule()
            lockStoppingSyncs(syncStates, stopSync)
            try {
                catalogue.clear(connectionId, signedIn)
            } finally {
                FolderSetup.lock.unlock()
            }
        }
    }

    // A run that started just as sign-out began says it's running only later, so every state is
    // watched until the lock is free.
    private suspend fun lockStoppingSyncs(syncStates: Flow<SyncState>, stopSync: () -> Unit) {
        coroutineScope {
            val stopper = launch { syncStates.collect { if (it.isRunning) stopQuietly(stopSync) } }
            FolderSetup.lock.lock()
            stopper.cancel()
        }
    }

    // Android refuses to start a service from the background, as stopping SyncService does. Its
    // run then goes on to its end and lets go of the lock; the sign-out still finishes.
    private fun stopQuietly(stopSync: () -> Unit) {
        try {
            stopSync()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Couldn't stop the sync; signing out once it ends", e)
        }
    }
}
