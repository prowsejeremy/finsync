package com.jpd.hz.adapter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavDeepLinkBuilder
import com.jpd.hz.R
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.adapter.run.SyncRun
import com.jpd.hz.adapter.run.SyncState
import com.jpd.hz.adapter.run.SyncStates
import com.jpd.hz.requestLibraryRescan
import com.jpd.hz.ui.ConnectionFragment
import com.jpd.hz.ui.MainActivity
import com.jpd.hz.ui.syncIncompleteMessage
import com.jpd.hz.ui.withUntaggedDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EXTRA_CONNECTION = "connection"
private const val TITLE_SEPARATOR = " · "

/**
 * The foreground sync (spec "Running connections"). Start asks carry a connection; they queue
 * without duplicates and run one at a time. Stop with a connection stops its run or takes it off
 * the queue; Stop without one, the notification's, stops the run and clears the queue.
 */
class SyncService : Service() {

    companion object {
        const val CHANNEL_ID      = "hz"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START    = "com.jpd.hz.action.START_SYNC"
        const val ACTION_STOP     = "com.jpd.hz.action.STOP_SYNC"

        fun start(context: Context, connectionId: String) {
            val intent = Intent(context, SyncService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CONNECTION, connectionId)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Stops [connectionId]'s run, or every run when it's null. */
        fun stop(context: Context, connectionId: String?) {
            val intent = Intent(context, SyncService::class.java).setAction(ACTION_STOP)
            connectionId?.let { intent.putExtra(EXTRA_CONNECTION, it) }
            context.startService(intent)
        }
    }

    // The queue is changed only on the main thread: here and in onStartCommand.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queue = SyncQueue()
    private var runJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val connectionId = intent?.getStringExtra(EXTRA_CONNECTION)
        when (intent?.action) {
            ACTION_START -> connectionId?.let(::enqueue) ?: stopIfIdle()
            ACTION_STOP -> stop(connectionId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun enqueue(connectionId: String) {
        // A foreground service must call startForeground soon after every start.
        val starting = buildNotification(getString(R.string.notification_starting), 0, null)
        startForeground(NOTIFICATION_ID, starting)
        if (!queue.add(connectionId)) return
        if (queue.running != null) {
            SyncStates.setWaiting(connectionId)
        } else {
            runNext()
        }
    }

    private fun runNext() {
        val connectionId = queue.next() ?: run {
            stopSelf()
            return
        }
        val connection = Platforms.connection(connectionId)
        if (connection == null) {
            // Signed out while it waited.
            SyncStates.setIdle(connectionId)
            queue.finish(connectionId)
            runNext()
            return
        }
        runJob = scope.launch {
            try {
                // Off the main thread, where the queue lives: a source may block on its network.
                withContext(Dispatchers.Default) {
                    SyncRun(this@SyncService, connectionId).run { state ->
                        updateNotification(connection, state)
                    }
                }
                leaveIncompleteNotice(connection, SyncStates.of(connectionId).value)
            } finally {
                // Stopped, failed or done: the player scans whatever reached the folder.
                requestLibraryRescan(this@SyncService)
                queue.finish(connectionId)
                runJob = null
                runNext()
            }
        }
    }

    private fun stop(connectionId: String?) {
        val dropped = if (connectionId == null) queue.clear() else listOfNotNull(
            connectionId.takeIf(queue::remove)
        )
        dropped.forEach(SyncStates::setIdle)
        val running = queue.running
        if (running != null && (connectionId == null || connectionId == running)) {
            SyncStates.setStopped(running)
            runJob?.cancel()
        } else {
            stopIfIdle()
        }
    }

    private fun stopIfIdle() {
        if (queue.running == null) stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.notification_channel_desc) }
        notificationManager.createNotificationChannel(channel)
    }

    // Opens the connection's page, whose Sync card shows the sync, with Adapters, Settings and
    // Home behind it. The component is set explicitly because the launcher activity is
    // PermissionsActivity, not MainActivity.
    private fun openPageIntent(connection: Connection?): PendingIntent =
        NavDeepLinkBuilder(this)
            .setComponentName(MainActivity::class.java)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.connectionFragment)
            .setArguments(connection?.let { ConnectionFragment.argsOf(it.platform) })
            .createPendingIntent()

    private fun buildNotification(
        text: String,
        progress: Int,
        connection: Connection?
    ): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, SyncService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleOf(connection))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentIntent(openPageIntent(connection))
            .addAction(R.drawable.ic_stop, getString(R.string.notification_stop), stopIntent)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotification(connection: Connection, state: SyncState) {
        val text = when {
            state.nothingChosen -> getString(R.string.sync_detail_nothing_chosen)
            state.errorMessage != null ->
                getString(R.string.notification_error, state.errorMessage)
            state.failedItems > 0 -> resources.syncIncompleteMessage(state.failedItems)
            !state.isRunning -> getString(R.string.notification_sync_complete)
            state.currentTrack.isNotEmpty() -> state.currentTrack
            else -> getString(R.string.notification_syncing)
        }
        val notification = buildNotification(text, state.progress, connection)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    // "hz · kurage"
    private fun titleOf(connection: Connection?): String {
        val app = getString(R.string.notification_title)
        return connection?.let { app + TITLE_SEPARATOR + it.name } ?: app
    }

    /**
     * Posted per connection, tagged with its ID, so one connection's notice never replaces
     * another's; dismissible, and that connection's next incomplete sync replaces it. A stopped or
     * failed sync has no failed items, so leaves none.
     */
    private fun leaveIncompleteNotice(connection: Connection, state: SyncState) {
        if (state.isRunning || state.failedItems == 0) return
        val text = resources.withUntaggedDetail(
            resources.syncIncompleteMessage(state.failedItems),
            state.untaggedFiles
        )
        val notice = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(titleOf(connection))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_sync)
            .setContentIntent(openPageIntent(connection))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(connection.id, NOTIFICATION_ID, notice)
    }
}
