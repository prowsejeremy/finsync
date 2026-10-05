package com.jpd.finsync.sync

import com.jpd.finsync.library.PlaylistBookRows
import com.jpd.finsync.library.isAlbumSelected
import com.jpd.finsync.library.selectsEveryAlbum
import com.jpd.finsync.model.MediaItem
import com.jpd.finsync.model.ServerCatalogue

/** What the user chose to sync. Empty playlist and book sets mean none (spec). */
data class SyncSelection(
    val albumIds: Set<String>,
    val playlistIds: Set<String>,
    val bookIds: Set<String>
)

/**
 * One sync's downloads, in order: selected albums' tracks, then tracks only selected playlists
 * need, then books, last so new music isn't stuck behind a large book (spec "Order").
 * [playlistIds] are the selected playlists in the catalogue, whose covers sync fetches.
 */
data class SyncPlan(
    val tracks: List<MediaItem>,
    val books: List<MediaItem>,
    val playlistIds: Set<String>
) {
    val items: List<MediaItem> get() = tracks + books
}

/**
 * Plans from the playlist rows the catalogue write returned ([written]), not the fetch, so a
 * playlist whose entries didn't load still plans, and keeps, its previous tracks (decision 3).
 */
fun syncPlanOf(
    catalogue: ServerCatalogue,
    written: PlaylistBookRows,
    selection: SyncSelection
): SyncPlan {
    val albumTracks = catalogue.audio.filter { isInAlbumSelection(it.albumId, selection.albumIds) }
    val audioById = catalogue.audio.associateBy { it.id }
    val playlistItems = written.playlistItems.filter { it.playlistId in selection.playlistIds }
    // Entries are looked up in the audio list, which carries the paths and media sources.
    val playlistTracks = playlistItems.mapNotNull { audioById[it.itemId] }
    // A book's synced_tracks row has no album, so it never becomes a partial album.
    val books = catalogue.books
        .filter { it.id in selection.bookIds }
        .map { it.copy(albumId = null) }
    return SyncPlan(
        tracks = (albumTracks + playlistTracks).distinctBy { it.id },
        books = books,
        playlistIds = written.playlists
            .filter { it.playlistId in selection.playlistIds }
            .mapTo(HashSet()) { it.playlistId }
    )
}

/**
 * Failed fetches as items that couldn't sync: one for each selected playlist or book whose server
 * data didn't load. Sync adds its failed downloads to make `SyncState.failedItems`.
 */
fun failedFetchCount(catalogue: ServerCatalogue, selection: SyncSelection): Int {
    val playlists = if (catalogue.playlistsFailed) {
        selection.playlistIds.size
    } else {
        selection.playlistIds.count { it in catalogue.failedPlaylistIds }
    }
    val books = if (catalogue.booksFailed) selection.bookIds.size else 0
    return playlists + books
}

/**
 * As before 3b: a specific album selection doesn't sync tracks without an album. Shared with
 * the Sync card's counts ([syncCountsOf]), so both apply one rule.
 */
internal fun isInAlbumSelection(albumId: String?, selectedAlbumIds: Set<String>): Boolean {
    if (albumId == null) return selectsEveryAlbum(selectedAlbumIds)
    return isAlbumSelected(albumId, selectedAlbumIds)
}
