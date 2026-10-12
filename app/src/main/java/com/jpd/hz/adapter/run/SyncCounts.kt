package com.jpd.hz.adapter.run

import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.choices.ChoiceRules

/** What the Sync card counts: the chosen songs and books, and how many are on the device. */
data class SyncCounts(
    val songsSynced: Int,
    val songsTotal: Int,
    val booksSynced: Int,
    val booksTotal: Int
) {
    val total: Int get() = songsTotal + booksTotal

    /** Every chosen song and book is on the device (also true with nothing chosen). */
    val allSynced: Boolean get() = songsSynced >= songsTotal && booksSynced >= booksTotal

    companion object {
        val NONE = SyncCounts(0, 0, 0, 0)
    }
}

/**
 * [planOf]'s choices over the stored catalogue, with no server call: every stored item in a
 * chosen group, once, counted as a song or a book by its kind. An item is synced when it has a
 * record ([syncedIds]).
 */
fun syncCountsOf(
    stored: StoredCatalogue,
    chosen: Map<ChoiceKind, Set<String>>,
    syncedIds: Set<String>
): SyncCounts {
    val kinds = stored.items.associate { it.id to it.kind }
    val planned = HashSet<String>()
    for (group in stored.groups) {
        val saved = chosen[group.kind] ?: continue
        if (ChoiceRules.isChosen(group.id, saved)) group.itemIds.filterTo(planned) { it in kinds }
    }
    val songs = planned.filter { kinds[it] == ItemKind.MUSIC }
    val books = planned.filter { kinds[it] == ItemKind.BOOK }
    return SyncCounts(
        songsSynced = songs.count { it in syncedIds },
        songsTotal = songs.size,
        booksSynced = books.count { it in syncedIds },
        booksTotal = books.size
    )
}
