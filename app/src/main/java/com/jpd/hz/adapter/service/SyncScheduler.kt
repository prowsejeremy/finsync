package com.jpd.hz.adapter.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.adapter.run.SyncRun
import com.jpd.hz.requestLibraryRescan
import java.util.concurrent.TimeUnit

private const val WORK_PREFIX = "hz_sync:"
// The one schedule before the harness, cancelled at launch so it can't run the old way.
private const val LEGACY_WORK = "hz_periodic_sync"
private const val KEY_CONNECTION = "connection"
private const val BACKOFF_MINUTES = 30L
private const val MAX_ATTEMPTS = 3

/**
 * Each connection's schedule (spec "Running connections"): unique periodic work
 * `hz_sync:<connectionId>`, waiting for a network only when its platform needs one. WorkManager
 * keeps periodic work across reboots by itself (spec H7).
 */
object SyncScheduler {

    fun schedule(
        context: Context,
        connectionId: String,
        intervalHours: Long,
        needsNetwork: Boolean
    ) {
        val network = if (needsNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_CONNECTION to connectionId))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workNameOf(connectionId),
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context, connectionId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workNameOf(connectionId))
    }

    fun cancelLegacy(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK)
    }

    fun workNameOf(connectionId: String): String = WORK_PREFIX + connectionId
}

/** One scheduled sync of the connection in its input data, then the player's rescan. */
class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val connectionId = inputData.getString(KEY_CONNECTION) ?: return Result.failure()
        // Signed out since it was scheduled: sign-out cancels the work, but one may be starting.
        Platforms.connection(connectionId) ?: return Result.failure()
        return try {
            SyncRun(context, connectionId).run()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        } finally {
            // Whatever the result, the player scans whatever reached the folder.
            requestLibraryRescan(context)
        }
    }
}
