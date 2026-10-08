package com.jpd.hz.library

private const val SELECT_ALL = "all"

/**
 * The album selection rule shared by sync and its Sync card. An empty selection, or one
 * containing "all", selects every album, matching how sync reads "selected_albums".
 */
fun isAlbumSelected(albumId: String, selectedIds: Set<String>): Boolean =
    selectsEveryAlbum(selectedIds) || selectedIds.contains(albumId)

/** True for an empty album selection or one containing "all". */
fun selectsEveryAlbum(selectedIds: Set<String>): Boolean =
    selectedIds.isEmpty() || selectedIds.contains(SELECT_ALL)
