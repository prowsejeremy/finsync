package com.jpd.finsync.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jpd.finsync.R
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.model.SyncState
import com.jpd.finsync.sync.SyncEngine
import com.jpd.finsync.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SyncService : Service() {

    companion object {
        const val CHANNEL_ID      = "finsync"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START    = "com.jpd.finsync.action.START_SYNC"
        const val ACTION_STOP     = "com.jpd.finsync.action.STOP_SYNC"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var syncJob: Job? = null
    private lateinit var repo: JellyfinRepository
    private lateinit var notificationManager: NotificationManager

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
            SyncEngine.syncLibrary(this@SyncService, config) { state ->
                updateNotification(state)
            }
            stopSelf()
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
            "Finsync",
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
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Finsync")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(state: SyncState) {
        val text = when {
            state.errorMessage != null       -> "Error: ${state.errorMessage}"
            !state.isRunning                 -> "Sync complete"
            state.currentTrack.isNotEmpty()  -> state.currentTrack
            else                             -> "Syncing..."
        }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(text, state.progress))
    }
}
