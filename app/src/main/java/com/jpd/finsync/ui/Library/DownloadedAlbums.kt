package com.jpd.finsync.ui

import com.jpd.finsync.db.SyncedAlbum
import com.jpd.finsync.library.isAlbumSelected

/**
 * The albums the Downloads list shows: those the album selection includes (see
 * [isAlbumSelected]), matching how sync reads the "selected_albums" preference.
 */
fun visibleDownloadedAlbums(
    albums: List<SyncedAlbum>,
    selectedIds: Set<String>
): List<SyncedAlbum> = albums.filter { isAlbumSelected(it.albumId, selectedIds) }
