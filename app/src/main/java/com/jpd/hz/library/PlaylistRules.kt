package com.jpd.hz.library

import com.jpd.hz.library.db.LibraryPlaylist
import com.jpd.hz.library.db.PlaylistEntryRow

private val PLAYLIST_ORDER: Comparator<PlaylistSummary> =
    compareBy<PlaylistSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.playlistId }

/**
 * Playlists: those with at least one song in the library, A–Z ignoring case. A playlist's cover is
 * the image beside its file, else its first song's album art.
 */
fun playlistSummaries(
    playlists: List<LibraryPlaylist>,
    entries: List<PlaylistEntryRow>
): List<PlaylistSummary> {
    val entriesByPlaylist = entries.groupBy { it.playlistId }
    return playlists
        .mapNotNull { playlist ->
            val songs = entriesByPlaylist[playlist.playlistId].orEmpty().sortedBy { it.position }
            if (songs.isEmpty()) return@mapNotNull null
            PlaylistSummary(
                playlistId = playlist.playlistId,
                name = playlist.name,
                songCount = songs.size,
                durationsMs = songs.map { it.durationMs },
                coverPath = playlist.coverPath ?: songs.first().artworkPath
            )
        }
        .sortedWith(PLAYLIST_ORDER)
}
