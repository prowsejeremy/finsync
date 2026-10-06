package com.jpd.hz.sync

import com.jpd.hz.db.CataloguePlaylistItem
import com.jpd.hz.db.TrackAlbumRow

/** What the Sync card counts: the selected songs and books, and how many are on the device. */
data class SyncCounts(
    val songsSynced: Int,
    val songsTotal: Int,
    val booksSynced: Int,
    val booksTotal: Int
) {
    val total: Int get() = songsTotal + booksTotal

    /** Every selected song and book is on the device (also true with nothing selected). */
    val allSynced: Boolean get() = songsSynced >= songsTotal && booksSynced >= booksTotal

    companion object {
        val NONE = SyncCounts(0, 0, 0, 0)
    }
}

/**
 * [syncPlanOf]'s rules over the stored catalogue (spec "Counts"), with no server call. Planned
 * songs are catalogue tracks in the album selection plus the catalogue tracks of selected
 * playlists' entries, each counted once; planned books are selected catalogue books. An item is
 * synced when it has a synced_tracks row ([syncedIds]), which books have too.
 */
fun syncCountsOf(
    tracks: List<TrackAlbumRow>,
    playlistItems: List<CataloguePlaylistItem>,
    bookIds: Set<String>,
    syncedIds: Set<String>,
    selection: SyncSelection
): SyncCounts {
    val trackIds = tracks.mapTo(HashSet()) { it.itemId }
    val songs = tracks
        .filter { isInAlbumSelection(it.albumId, selection.albumIds) }
        .mapTo(HashSet()) { it.itemId }
    // As in syncPlanOf, an entry counts only when its item is a catalogue track.
    playlistItems
        .filter { it.playlistId in selection.playlistIds && it.itemId in trackIds }
        .mapTo(songs) { it.itemId }
    val books = selection.bookIds.filter { it in bookIds }
    return SyncCounts(
        songsSynced = songs.count { it in syncedIds },
        songsTotal = songs.size,
        booksSynced = books.count { it in syncedIds },
        booksTotal = books.size
    )
}
