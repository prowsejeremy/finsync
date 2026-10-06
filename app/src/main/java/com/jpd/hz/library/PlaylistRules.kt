package com.jpd.hz.library

import com.jpd.hz.db.CatalogueBook
import com.jpd.hz.db.CatalogueBookChapter
import com.jpd.hz.db.CataloguePlaylist
import com.jpd.hz.db.CataloguePlaylistItem
import com.jpd.hz.db.PlaylistEntryRow
import com.jpd.hz.model.ServerCatalogue
import com.jpd.hz.model.ServerPlaylist

private const val AUDIO_TYPE = "Audio"

private val PLAYLIST_ORDER: Comparator<PlaylistSummary> =
    compareBy<PlaylistSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.playlistId }

/** The playlist and book rows of one catalogue write (books arrive in M2). */
data class PlaylistBookRows(
    val playlists: List<CataloguePlaylist>,
    val playlistItems: List<CataloguePlaylistItem>,
    val books: List<CatalogueBook> = emptyList(),
    val chapters: List<CatalogueBookChapter> = emptyList()
)

/**
 * Keeps each playlist's audio entries in server order, a song repeated where the playlist repeats
 * it, numbered from 0. A playlist with no audio entries isn't an audio playlist, so it's left out
 * (decision 2).
 */
fun playlistRowsFrom(playlists: List<ServerPlaylist>): PlaylistBookRows {
    val audioPlaylists = playlists.mapNotNull { playlist ->
        val audio = playlist.entries.filter { it.type == AUDIO_TYPE }
        if (audio.isEmpty()) null else playlist.playlist to audio
    }
    return PlaylistBookRows(
        playlists = audioPlaylists.map { (playlist, _) ->
            CataloguePlaylist(playlist.id, playlist.name)
        },
        playlistItems = audioPlaylists.flatMap { (playlist, audio) ->
            audio.mapIndexed { position, entry ->
                CataloguePlaylistItem(playlist.id, position, entry.id)
            }
        }
    )
}

/**
 * The rows to write when parts of the fetch failed: a failed part keeps its [previous] rows, so
 * cleanup never deletes files a failed item might own (spec "Order, cleanup and failures").
 */
fun keepFailedParts(
    fresh: PlaylistBookRows,
    previous: PlaylistBookRows,
    catalogue: ServerCatalogue
): PlaylistBookRows {
    val keptPlaylistIds = if (catalogue.playlistsFailed) {
        previous.playlists.mapTo(HashSet()) { it.playlistId }
    } else {
        catalogue.failedPlaylistIds
    }
    return PlaylistBookRows(
        playlists = fresh.playlists.filterNot { it.playlistId in keptPlaylistIds } +
            previous.playlists.filter { it.playlistId in keptPlaylistIds },
        playlistItems = fresh.playlistItems.filterNot { it.playlistId in keptPlaylistIds } +
            previous.playlistItems.filter { it.playlistId in keptPlaylistIds },
        books = if (catalogue.booksFailed) previous.books else fresh.books,
        chapters = if (catalogue.booksFailed) previous.chapters else fresh.chapters
    )
}

/**
 * Playlists: the selected ones with at least one downloaded song, A–Z ignoring case (spec).
 * [coverPath] gets each listed playlist's first downloaded entry, for the fallback cover.
 */
fun playlistSummaries(
    playlists: List<CataloguePlaylist>,
    entries: List<PlaylistEntryRow>,
    selectedIds: Set<String>,
    coverPath: (playlistId: String, firstEntry: PlaylistEntryRow) -> String?
): List<PlaylistSummary> {
    val entriesByPlaylist = entries.groupBy { it.playlistId }
    return playlists
        .filter { it.playlistId in selectedIds }
        .mapNotNull { playlist ->
            val downloaded = entriesByPlaylist[playlist.playlistId].orEmpty()
                .sortedBy { it.position }
            if (downloaded.isEmpty()) return@mapNotNull null
            PlaylistSummary(
                playlistId = playlist.playlistId,
                name = playlist.name,
                songCount = downloaded.size,
                durationsMs = downloaded.map { it.durationMs },
                coverPath = coverPath(playlist.playlistId, downloaded.first())
            )
        }
        .sortedWith(PLAYLIST_ORDER)
}
