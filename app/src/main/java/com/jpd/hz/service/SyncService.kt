package com.jpd.hz.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.navigation.NavDeepLinkBuilder
import com.jpd.hz.R
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.model.SyncState
import com.jpd.hz.requestLibraryRescan
import com.jpd.hz.sync.SyncEngine
import com.jpd.hz.ui.MainActivity
import com.jpd.hz.ui.syncIncompleteMessage
import com.jpd.hz.ui.withUntaggedDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SyncService : Service() {

    companion object {
        const val CHANNEL_ID      = "hz"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START    = "com.jpd.hz.action.START_SYNC"
        const val ACTION_STOP     = "com.jpd.hz.action.STOP_SYNC"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncJob: Job? = null
    private lateinit var repo: JellyfinRepository
    private lateinit var notificationManager: NotificationManager

    // Opens Settings → Sync, whose Sync card shows the sync, with Settings and Home behind it.
    // The component is set explicitly because the launcher activity is PermissionsActivity, not
    // MainActivity.
    private val openSettingsIntent: PendingIntent by lazy {
        NavDeepLinkBuilder(this)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.syncSettingsFragment)
            .createPendingIntent()
    }

    override fun onCreate() {
        super.onCreate()
        repo = JellyfinRepository(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSync()
            ACTION_STOP  -> stopSync()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun startSync() {
        val config = repo.getSavedConfig() ?: run { stopSelf(); return }
        startForeground(NOTIFICATION_ID, buildNotification("Starting sync...", 0))

        syncJob = scope.launch {
            try {
                SyncEngine.syncLibrary(this@SyncService, config) { state ->
                    updateNotification(state)
                }
                leaveIncompleteNotice(SyncEngine.syncState.value)
                stopSelf()
            } finally {
                // Stopped, failed or done: the player scans whatever reached the folder.
                requestLibraryRescan(this@SyncService)
            }
        }
    }

    private fun stopSync() {
        SyncEngine.emitStopped()
        repo.cancelAudioDownload()
        syncJob?.cancel()
        syncJob = null
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "hz",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Syncing your music library" }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SyncService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("hz")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentIntent(openSettingsIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(state: SyncState) {
        val text = when {
            state.errorMessage != null       -> "Error: ${state.errorMessage}"
            state.failedItems > 0            -> resources.syncIncompleteMessage(state.failedItems)
            !state.isRunning                 -> "Sync complete"
            state.currentTrack.isNotEmpty()  -> state.currentTrack
            else                             -> "Syncing..."
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, state.progress))
    }

    /**
     * Detached, so it outlives the service; dismissible, and the next sync's notification
     * replaces it (same ID). A stopped or failed sync has no failed items, so leaves none.
     */
    private fun leaveIncompleteNotice(state: SyncState) {
        if (state.isRunning || state.failedItems == 0) return
        stopForeground(STOP_FOREGROUND_DETACH)
        val text = resources.withUntaggedDetail(
            resources.syncIncompleteMessage(state.failedItems),
            state.untaggedFiles
        )
        val notice = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("hz")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_sync)
            .setContentIntent(openSettingsIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID, notice)
    }
}
