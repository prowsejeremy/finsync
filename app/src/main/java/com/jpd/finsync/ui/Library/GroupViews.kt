package com.jpd.finsync.ui

import android.content.res.Resources
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.jpd.finsync.R
import com.jpd.finsync.library.SongRow

/**
 * An artist's round photo, else their initials, else a person icon. The three views sit in one
 * round surface_2 card, and only one of them shows.
 */
fun bindAvatar(
    photo: ImageView,
    initials: TextView,
    personIcon: ImageView,
    name: String,
    photoPath: String?
) {
    val letters = if (photoPath == null) initialsOf(name) else null
    photo.visibility = if (photoPath != null) View.VISIBLE else View.GONE
    initials.visibility = if (letters != null) View.VISIBLE else View.GONE
    personIcon.visibility = if (photoPath == null && letters == null) View.VISIBLE else View.GONE
    initials.text = letters
    loadArtwork(photo, photoPath)
}

/** Fills stacked covers front to back from [coverPaths] (album order); missing ones stay blank. */
fun bindStackedCovers(
    front: ImageView,
    middle: ImageView,
    back: ImageView,
    coverPaths: List<String?>
) {
    listOf(front, middle, back).forEachIndexed { index, cover ->
        loadArtwork(cover, coverPaths.getOrNull(index))
    }
}

/** "3 h 2 min", "3 h" or "41 min". */
fun formatListLength(resources: Resources, trackDurationsMs: List<Long?>): String =
    when (val length = listLengthOf(trackDurationsMs)) {
        is ListLength.Minutes ->
            resources.getString(R.string.album_length_minutes, length.minutes)
        is ListLength.Hours -> resources.getString(R.string.list_length_hours, length.hours)
        is ListLength.HoursMinutes ->
            resources.getString(R.string.list_length_hours_minutes, length.hours, length.minutes)
    }

/** "41 songs · 3 h 2 min". */
fun songsSummary(resources: Resources, songs: List<SongRow>): String = joinWithDots(
    listOf(
        resources.getQuantityString(R.plurals.song_count, songs.size, songs.size),
        formatListLength(resources, songs.map { it.durationMs })
    )
)
