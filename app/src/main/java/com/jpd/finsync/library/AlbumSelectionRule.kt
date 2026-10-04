package com.jpd.finsync.library

private const val SELECT_ALL = "all"

/**
 * The album selection rule shared by sync, Downloads and Albums. An empty selection, or one
 * containing "all", selects every album, matching how sync reads "selected_albums".
 */
fun isAlbumSelected(albumId: String, selectedIds: Set<String>): Boolean =
    selectedIds.isEmpty() || selectedIds.contains(SELECT_ALL) || selectedIds.contains(albumId)
