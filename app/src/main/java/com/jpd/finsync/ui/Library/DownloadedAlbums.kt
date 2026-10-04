package com.jpd.finsync.ui

import com.jpd.finsync.db.SyncedAlbum

/**
 * The albums the Downloads list shows. An empty selection, or one containing "all", means every
 * album, matching how sync reads the "selected_albums" preference.
 */
fun visibleDownloadedAlbums(
    albums: List<SyncedAlbum>,
    selectedIds: Set<String>
): List<SyncedAlbum> {
    val showAll = selectedIds.isEmpty() || selectedIds.contains("all")
    return if (showAll) albums else albums.filter { selectedIds.contains(it.albumId) }
}
