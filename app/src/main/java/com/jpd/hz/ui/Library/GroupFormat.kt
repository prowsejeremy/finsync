package com.jpd.hz.ui

import com.jpd.hz.library.AlbumSummary
import com.jpd.hz.library.GroupDetail
import com.jpd.hz.library.SongRow

private const val MAX_INITIALS = 2
private const val MINUTES_PER_HOUR = 60L
private const val STACKED_COVERS = 3
private val WORD_SEPARATOR = Regex("\\s+")

/**
 * Up to two initials for an artist without a photo: the first letter of each of the first two
 * words that have one ("Lucy Harrow" is LH, "Radiohead" is R). Null when the name has no
 * letters, so the row shows a person icon instead.
 */
fun initialsOf(name: String): String? {
    val initials = name.split(WORD_SEPARATOR)
        .mapNotNull { word -> word.firstOrNull { it.isLetter() } }
        .take(MAX_INITIALS)
        .joinToString("") { it.uppercase() }
    return initials.ifEmpty { null }
}

/** How a song list's length reads: minutes under an hour, else hours and any minutes left. */
sealed class ListLength {
    data class Minutes(val minutes: Long) : ListLength()
    data class Hours(val hours: Long) : ListLength()
    data class HoursMinutes(val hours: Long, val minutes: Long) : ListLength()
}

/** Rounds to the nearest minute as album detail does (sub-project 2), then splits off hours. */
fun listLengthOf(trackDurationsMs: List<Long?>): ListLength {
    val totalMinutes = albumLengthMinutes(trackDurationsMs)
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return when {
        hours == 0L -> ListLength.Minutes(minutes)
        minutes == 0L -> ListLength.Hours(hours)
        else -> ListLength.HoursMinutes(hours, minutes)
    }
}

/** A row in the artist or genre page's list, below Play and Shuffle. */
sealed class GroupRow {
    data class AllSongs(val coverPaths: List<String?>, val songs: List<SongRow>) : GroupRow()
    object AlbumsLabel : GroupRow()
    data class Album(val album: AlbumSummary) : GroupRow()
}

/** All songs when the rule allows it, then an ALBUMS label and the albums, newest first. */
fun groupRows(group: GroupDetail): List<GroupRow> {
    val rows = mutableListOf<GroupRow>()
    if (group.showsAllSongs) rows.add(GroupRow.AllSongs(stackedCovers(group), group.songs))
    if (group.albums.isNotEmpty()) {
        rows.add(GroupRow.AlbumsLabel)
        group.albums.forEach { rows.add(GroupRow.Album(it)) }
    }
    return rows
}

/** The covers of the group's first three albums, in album order. */
fun stackedCovers(group: GroupDetail): List<String?> =
    group.albums.take(STACKED_COVERS).map { it.artworkPath }
