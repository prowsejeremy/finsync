package com.jpd.finsync.ui

import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.jpd.finsync.R
import kotlin.math.roundToInt

// Android-side helpers, kept out of SyncDisplay so that class stays JVM-testable.

@get:StringRes
val SyncDisplay.Status.labelRes: Int
    get() = when (this) {
        SyncDisplay.Status.OFFLINE    -> R.string.status_offline
        SyncDisplay.Status.SYNCING    -> R.string.status_syncing
        SyncDisplay.Status.STOPPED    -> R.string.status_sync_stopped
        SyncDisplay.Status.FAILED     -> R.string.status_sync_failed
        SyncDisplay.Status.SYNCED     -> R.string.status_synced
        SyncDisplay.Status.NOT_SYNCED -> R.string.status_not_synced
    }

@get:ColorRes
val SyncDisplay.Status.labelColorRes: Int
    get() = if (this == SyncDisplay.Status.SYNCED) R.color.accent_green else R.color.muted

@get:StringRes
val SyncDisplay.buttonLabelRes: Int
    get() = if (status == SyncDisplay.Status.SYNCING) R.string.btn_stop_sync else R.string.btn_sync_now

/** Shows [progress] from 0 to 1, or an indeterminate animation when it's null. */
fun BaseProgressIndicator<*>.showSyncProgress(progress: Float?) {
    if (progress == null) {
        if (!isIndeterminate) {
            // Some Material versions refuse to switch to indeterminate while visible.
            visibility = View.INVISIBLE
            isIndeterminate = true
        }
    } else {
        // On an indeterminate indicator, this switches to determinate after the current cycle.
        setProgressCompat((progress * max).roundToInt(), true)
    }
    visibility = View.VISIBLE
}

/**
 * Hides the indicator and resets it to zero. Otherwise the next sync's first progress update
 * would animate backwards from wherever the last sync left off. Indeterminate indicators are
 * left alone, because setting their progress queues a switch to determinate.
 */
fun BaseProgressIndicator<*>.hideSyncProgress() {
    visibility = View.GONE
    if (!isIndeterminate) setProgressCompat(0, false)
}
