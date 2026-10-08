package com.jpd.hz.ui

import android.content.res.Resources
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.jpd.hz.R
import com.jpd.hz.sync.SyncCounts
import java.text.NumberFormat
import kotlin.math.roundToInt

// Android-side helpers, kept out of SyncDisplay so that class stays JVM-testable.

@get:StringRes
val SyncDisplay.Status.labelRes: Int
    get() = when (this) {
        SyncDisplay.Status.OFFLINE    -> R.string.status_offline
        SyncDisplay.Status.SYNCING    -> R.string.status_syncing
        SyncDisplay.Status.STOPPED    -> R.string.status_sync_stopped
        SyncDisplay.Status.FAILED     -> R.string.status_sync_failed
        SyncDisplay.Status.INCOMPLETE -> R.string.status_sync_incomplete
        SyncDisplay.Status.SYNCED     -> R.string.status_synced
        SyncDisplay.Status.NOT_SYNCED -> R.string.status_not_synced
    }

@get:ColorRes
val SyncDisplay.Status.labelColorRes: Int
    get() = if (this == SyncDisplay.Status.SYNCED) R.color.status_good else R.color.muted

@get:StringRes
val SyncDisplay.buttonLabelRes: Int
    get() = if (status == SyncDisplay.Status.SYNCING) R.string.btn_stop_sync else R.string.btn_sync_now

/** "2 items couldn't sync. They'll retry next sync." */
fun Resources.syncIncompleteDetail(failedItems: Int): String =
    getQuantityString(R.plurals.sync_incomplete_detail, failedItems, failedItems)

/** The same with "Sync incomplete: " in front, for the sync notification. */
fun Resources.syncIncompleteMessage(failedItems: Int): String =
    getString(R.string.sync_incomplete_message, syncIncompleteDetail(failedItems))

/** "2 files couldn't be tagged." (T2), or null when every file was tagged. */
fun Resources.syncUntaggedDetail(untaggedFiles: Int): String? =
    if (untaggedFiles > 0) {
        getQuantityString(R.plurals.sync_untagged_detail, untaggedFiles, untaggedFiles)
    } else {
        null
    }

/** [line] with the tagging sentence after it, when there is one. Null when both are. */
fun Resources.withUntaggedDetail(line: String?, untaggedFiles: Int): String? =
    listOfNotNull(line, syncUntaggedDetail(untaggedFiles)).joinToString(" ").ifEmpty { null }

/**
 * The Sync card's counts line (spec "Sync card detail line"): "1,280 of 1,400 songs synced",
 * "1,280 of 1,400 songs · 0 of 2 books synced" or "0 of 2 books synced". Null when nothing is
 * selected.
 */
fun Resources.syncCountsLine(counts: SyncCounts): String? =
    syncCountsText(counts)?.let { getString(R.string.sync_counts_synced, it) }

/**
 * The same counts without "synced", for the Settings Sync row (spec "Sync row summary"):
 * "1,280 of 1,400 songs", "1,280 of 1,400 songs · 0 of 2 books" or "0 of 2 books". Null when
 * nothing is selected.
 */
fun Resources.syncCountsText(counts: SyncCounts): String? {
    val parts = listOfNotNull(
        countPart(R.plurals.sync_songs_of, counts.songsSynced, counts.songsTotal),
        countPart(R.plurals.sync_books_of, counts.booksSynced, counts.booksTotal)
    )
    return if (parts.isEmpty()) null else joinWithDots(parts)
}

/** What the Sync row shows after its label: "45%", the counts, or null for the label alone. */
fun Resources.syncRowDetail(summary: SyncRowSummary): String? = when {
    summary.percent != null -> getString(R.string.settings_sync_row_percent, summary.percent)
    summary.counts != null -> syncCountsText(summary.counts)
    else -> null
}

/** A running sync's "45 of 1,402 items", or null before its total is known. */
fun Resources.syncRunLine(done: Int, total: Int): String? =
    countPart(R.plurals.sync_items_of, done, total)

// "1,280 of 1,400 songs", with the plural picked by the total; null when the total is 0.
private fun Resources.countPart(@PluralsRes pluralsRes: Int, done: Int, total: Int): String? {
    if (total <= 0) return null
    return getQuantityString(pluralsRes, total, formatCount(done), formatCount(total))
}

// The one formatter for these counts: the locale's digit grouping ("1,280"). The plurals take
// it as a string (%1$s), because %d wouldn't group.
private fun formatCount(count: Int): String =
    NumberFormat.getIntegerInstance().format(count.toLong())

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
