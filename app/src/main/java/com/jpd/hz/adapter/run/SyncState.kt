package com.jpd.hz.adapter.run

private const val PERCENT = 100

/** One connection's sync, as its Sync card, notification and Home's ring show it. */
data class SyncState(
    val totalItems: Int = 0,
    val downloadedItems: Int = 0,
    val currentTrack: String = "",
    val isRunning: Boolean = false,
    val errorMessage: String? = null,
    /** True when the user explicitly stopped the sync (distinct from an error). */
    val wasStopped: Boolean = false,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    /** True only on the terminal emission after a successful (non-stopped, non-error) sync run. */
    val syncComplete: Boolean = false,
    /**
     * Items this run couldn't sync, set on the terminal emission: failed fetches plus failed
     * downloads. Above zero, the sync is incomplete and they retry next sync (3b spec).
     */
    val failedItems: Int = 0,
    /**
     * Files this run couldn't tag, set on the terminal emission. They play with their own tags,
     * and the next sync tries again. It doesn't make the sync incomplete (T2 spec).
     */
    val untaggedFiles: Int = 0,
    /** Queued behind another connection's run (spec H9.4); [isRunning] is true meanwhile. */
    val waiting: Boolean = false,
    /** The run didn't start because nothing is chosen (spec H4). */
    val nothingChosen: Boolean = false
) {
    val progress: Int get() = if (totalItems > 0) (downloadedItems * PERCENT) / totalItems else 0
}
