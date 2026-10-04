package com.jpd.finsync.ui

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val MS_PER_MINUTE = 60_000.0
private const val DOT_SEPARATOR = " · "

/** m:ss, or h:mm:ss from an hour. Negative durations show as 0:00. */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs.coerceAtLeast(0L) / MS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** An album's length in whole minutes, rounded to the nearest minute. */
fun albumLengthMinutes(trackDurationsMs: List<Long?>): Long =
    Math.round(trackDurationsMs.sumOf { it ?: 0L } / MS_PER_MINUTE)

/** Joins the parts that are present with " · ", as in "2018 · 13 tracks · 79 min". */
fun joinWithDots(parts: List<String?>): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(DOT_SEPARATOR)
