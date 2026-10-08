package com.jpd.hz.ui

import com.jpd.hz.library.db.LibraryTrack

private const val DEFAULT_DISC = 1
private const val ARTIST_SEPARATOR = ", "

/** A row in album detail's track list. */
sealed class AlbumListRow {

    data class DiscHeading(val discNumber: Int) : AlbumListRow()

    data class Track(
        val itemId: String,
        val number: String,
        val title: String,
        /** The track's artists, only when they differ from the album artist. */
        val artists: String?,
        val durationMs: Long?,
        /** Position in the album's tracks; playback starts from here. */
        val queueIndex: Int
    ) : AlbumListRow()
}

/**
 * Album detail's rows. "Disc N" headings appear only when the tracks span more than one disc; a
 * track without a disc number counts as disc 1.
 */
fun albumListRows(tracks: List<LibraryTrack>, albumArtist: String?): List<AlbumListRow> {
    val multiDisc = tracks.map { it.discNumber ?: DEFAULT_DISC }.distinct().size > 1
    val rows = mutableListOf<AlbumListRow>()
    var currentDisc: Int? = null
    tracks.forEachIndexed { index, track ->
        val disc = track.discNumber ?: DEFAULT_DISC
        if (multiDisc && disc != currentDisc) rows.add(AlbumListRow.DiscHeading(disc))
        currentDisc = disc
        rows.add(
            AlbumListRow.Track(
                itemId = track.trackId,
                number = track.trackNumber?.toString() ?: "",
                title = track.title,
                artists = trackArtists(track.artistNames, albumArtist),
                durationMs = track.durationMs,
                queueIndex = index
            )
        )
    }
    return rows
}

/** The track's artists, or null when they're just the album artist (or unknown). */
fun trackArtists(artistNames: List<String>, albumArtist: String?): String? {
    if (artistNames.isEmpty()) return null
    val sameAsAlbum = artistNames.size == 1 &&
        artistNames[0].equals(albumArtist, ignoreCase = true)
    return if (sameAsAlbum) null else artistNames.joinToString(ARTIST_SEPARATOR)
}
