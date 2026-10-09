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

/**
 * What Select albums saves from [checked] of [total] listed albums (spec "Sign-in health",
 * decision 5): "all" when every album is ticked, else the ticked IDs. Null, saving nothing, when
 * no list loaded: an empty or "all" selection would sync every album.
 */
fun albumIdsToSave(checked: Set<String>, total: Int): Set<String>? = when {
    total == 0 -> null
    checked.size == total -> setOf(SELECT_ALL)
    else -> checked.toSet()
}
