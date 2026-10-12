package com.jpd.hz.adapter.run

import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.SourceItem

/** One connection's catalogue as stored: its groups and what the counts need of its items. */
data class StoredCatalogue(val groups: List<ChoiceGroup>, val items: List<StoredItem>) {
    companion object {
        val EMPTY = StoredCatalogue(emptyList(), emptyList())
    }
}

data class StoredItem(
    val id: String,
    val kind: ItemKind,
    val label: String,
    val durationMs: Long?,
    val size: Long?
)

/**
 * What to store from a fetch: a kind that didn't load, or a group whose entries didn't, keeps
 * its [previous] groups and the items they hold, so cleanup never deletes files a failed part may
 * own and the counts hold steady (spec "The sync run", step 3). Kept groups go after the fresh
 * ones, as before the harness.
 */
fun mergeFailedParts(fresh: SourceCatalogue, previous: StoredCatalogue): StoredCatalogue {
    val kept = previous.groups.filter {
        it.kind in fresh.failedKinds || it.id in fresh.failedGroupIds
    }
    val keptKeys = kept.mapTo(HashSet()) { it.kind to it.id }
    // A failed kind's previous groups stand for the whole kind, as before the harness.
    val groups = fresh.groups.filterNot {
        it.kind in fresh.failedKinds || (it.kind to it.id) in keptKeys
    } + kept
    val freshItems = fresh.items.map(::storedItemOf)
    val freshIds = freshItems.mapTo(HashSet()) { it.id }
    val keptItemIds = kept.flatMapTo(HashSet()) { it.itemIds }
    val keptItems = previous.items.filter { it.id in keptItemIds && it.id !in freshIds }
    return StoredCatalogue(groups, freshItems + keptItems)
}

fun storedItemOf(item: SourceItem) =
    StoredItem(item.id, item.kind, item.label, item.durationMs, item.size)
