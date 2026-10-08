package com.jpd.hz.sync

import android.content.Context
import android.util.Log
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.model.SyncState
import com.jpd.hz.service.SyncScheduler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "JellyfinSignOut"
private const val PREFS = "settings"
// The Auto-sync screen's choice, which reads "Disabled" once the schedule is gone.
private const val AUTO_SYNC_INTERVAL = "auto_sync_interval"
private const val AUTO_SYNC_OFF = "disabled"

/**
 * Signing out of Jellyfin (spec "T4 amendment" B1, B2). It clears the sign-in, the server's
 * catalogue and the auto-sync schedule.
 *
 * It keeps the files, `adapter_folders`, the sync records and the album, playlist and book
 * selections. An empty album selection means every album and an empty book selection means none,
 * so clearing them would make the next sync download the whole server and delete every synced
 * book; with the records kept, signing in again to the same server downloads and re-tags nothing.
 * The player's queue, book progress and playback aren't Jellyfin's, so they stay too (D10).
 */
class JellyfinSignOut internal constructor(
    context: Context,
    private val catalogue: JellyfinCatalogue,
    private val clearSignIn: suspend () -> Unit,
    private val cancelSchedule: () -> Unit,
    private val signedIn: () -> Boolean
) {

    constructor(context: Context) : this(
        context.applicationContext,
        JellyfinCatalogue(context),
        { JellyfinRepository(context.applicationContext).logout(context.applicationContext) },
        { SyncScheduler.cancelPeriodicSync(context.applicationContext) },
        { JellyfinRepository(context.applicationContext).getSavedConfig() != null }
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * In an order that leaves nothing half-done:
     * 1. The sign-in goes first, so no sync, catalogue refresh or scheduled sync starts again.
     * 2. The schedule goes, which also stops a scheduled sync that's running.
     * 3. It waits for [FolderSetup.lock], stopping any sync that runs meanwhile with [stopSync],
     *    so a sync's catalogue write can't land after the clear.
     * 4. The catalogue goes, unless the user has signed in again meanwhile.
     * Nothing in it can be cancelled. Cut short after step 1, the next sign-in's refresh replaces
     * the catalogue.
     */
    suspend fun run(syncStates: Flow<SyncState>, stopSync: () -> Unit) {
        withContext(NonCancellable) {
            clearSignIn()
            prefs.edit().putString(AUTO_SYNC_INTERVAL, AUTO_SYNC_OFF).apply()
            cancelSchedule()
            lockStoppingSyncs(syncStates, stopSync)
            try {
                catalogue.clear(signedIn)
            } finally {
                FolderSetup.lock.unlock()
            }
        }
    }

    // A sync that started just as sign-out began says it's running only later, so every state
    // is watched until the lock is free.
    private suspend fun lockStoppingSyncs(syncStates: Flow<SyncState>, stopSync: () -> Unit) {
        coroutineScope {
            val stopper = launch { syncStates.collect { if (it.isRunning) stopQuietly(stopSync) } }
            FolderSetup.lock.lock()
            stopper.cancel()
        }
    }

    // Android refuses to start a service from the background, as stopping SyncService does. Its
    // sync then runs on to its end and lets go of the lock; the sign-out still finishes.
    private fun stopQuietly(stopSync: () -> Unit) {
        try {
            stopSync()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Couldn't stop the sync; signing out once it ends", e)
        }
    }
}
