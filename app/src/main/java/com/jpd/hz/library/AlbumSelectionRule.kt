package com.jpd.hz.library

private const val SELECT_ALL = "all"

/**
 * The album selection rule shared by sync and Albums. An empty selection, or one
 * containing "all", selects every album, matching how sync reads "selected_albums".
 */
fun isAlbumSelected(albumId: String, selectedIds: Set<String>): Boolean =
    selectsEveryAlbum(selectedIds) || selectedIds.contains(albumId)

/** True for an empty album selection or one containing "all". */
fun selectsEveryAlbum(selectedIds: Set<String>): Boolean =
    selectedIds.isEmpty() || selectedIds.contains(SELECT_ALL)

/**
 * The album selection browse screens apply once selected playlists count (spec "Visibility"):
 * albums holding a downloaded entry of a selected playlist join a specific selection. A
 * selection of everything stays as it is, since adding IDs to an empty set would narrow it.
 */
fun visibleAlbumSelection(selectedIds: Set<String>, playlistAlbumIds: Set<String>): Set<String> =
    if (selectsEveryAlbum(selectedIds)) selectedIds else selectedIds + playlistAlbumIds
