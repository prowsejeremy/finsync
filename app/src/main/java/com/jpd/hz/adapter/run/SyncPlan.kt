package com.jpd.hz.adapter.run

import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.SourceItem
import com.jpd.hz.adapter.choices.ChoiceRules

/**
 * One run's items in order: chosen albums' and folders' items in the source's order, then items
 * only chosen playlists need, then chosen books, each once (spec "The sync run", step 4).
 * [playlists] are the chosen playlists with their items, repeats included, for their files.
 * [covers] are the groups that keep a cover beside their planned items.
 */
data class SyncPlan(
    val items: List<SourceItem>,
    val playlists: List<PlannedPlaylist>,
    val covers: List<PlannedCover>
)

/**
 * A chosen playlist's items in its order. An item that didn't download stays in, so the player
 * skips it and the next sync fills it in (spec "Playlist files").
 */
data class PlannedPlaylist(val group: ChoiceGroup, val items: List<SourceItem>)

/** An album or book with planned items: a cover beside each, fetched beside the first. */
data class PlannedCover(val group: ChoiceGroup, val items: List<SourceItem>)

/**
 * Plans from [groups], the catalogue as stored (a failed part keeps its previous groups), and
 * [items], this fetch's: an item the fetch didn't list isn't planned. [chosen] holds each kind's
 * saved choice.
 */
fun planOf(
    groups: List<ChoiceGroup>,
    items: List<SourceItem>,
    chosen: Map<ChoiceKind, Set<String>>
): SyncPlan {
    val byId = items.associateBy { it.id }
    val planned = LinkedHashMap<String, SourceItem>()
    var playlists = emptyList<PlannedPlaylist>()
    for (kind in ChoiceKind.values().sortedBy { it.planOrder }) {
        val saved = chosen[kind] ?: continue
        val kindGroups = groups.filter { it.kind == kind && ChoiceRules.isChosen(it.id, saved) }
        if (kind == ChoiceKind.PLAYLIST) {
            playlists = kindGroups.map { group ->
                PlannedPlaylist(group, group.itemIds.mapNotNull(byId::get))
            }
            playlists.flatMap { it.items }.forEach { planned.putIfAbsent(it.id, it) }
        } else {
            val ids = kindGroups.flatMapTo(HashSet()) { it.itemIds }
            items.filter { it.id in ids }.forEach { planned.putIfAbsent(it.id, it) }
        }
    }
    val plannedItems = planned.values.toList()
    return SyncPlan(plannedItems, playlists, coversOf(groups, plannedItems))
}

/**
 * How many chosen groups couldn't sync because their part of the fetch failed: each chosen group
 * of a kind that didn't load, and each chosen group whose entries didn't load. The run adds its
 * failed downloads (spec "The sync run").
 */
fun failedChoiceCount(
    catalogue: SourceCatalogue,
    stored: List<ChoiceGroup>,
    chosen: Map<ChoiceKind, Set<String>>
): Int {
    val failedKinds = chosen.entries
        .filter { it.key in catalogue.failedKinds }
        .sumOf { (kind, saved) ->
            // `all` over a list that never loaded still counts once, so the run is incomplete.
            if (ChoiceRules.selectsEvery(saved)) {
                maxOf(1, stored.count { it.kind == kind })
            } else {
                saved.size
            }
        }
    val kindOf = stored.associate { it.id to it.kind }
    val failedGroups =
        catalogue.failedGroupIds.count { isChosenFailedGroup(it, kindOf, catalogue, chosen) }
    return failedKinds + failedGroups
}

/**
 * Whether this run may clean up (spec "The sync run", step 5). Not when a part the user chose
 * didn't load: its files aren't in the plan, and after a sign-out (which clears the catalogue) or
 * a rebuilt database (which has no records) nothing else may know them, so cleanup would delete
 * them. Not when the plan is empty: everything chosen has gone from the source, or a fetch came
 * back empty, and the folder's files stay until the choices change.
 */
fun mayCleanUp(
    catalogue: SourceCatalogue,
    stored: List<ChoiceGroup>,
    chosen: Map<ChoiceKind, Set<String>>,
    plan: SyncPlan
): Boolean {
    if (plan.items.isEmpty()) return false
    if (chosen.any { (kind, saved) -> kind in catalogue.failedKinds && saved.isNotEmpty() }) {
        return false
    }
    val kindOf = stored.associate { it.id to it.kind }
    return catalogue.failedGroupIds.none { isChosenFailedGroup(it, kindOf, catalogue, chosen) }
}

// A failed group of a kind that loaded, which the choices name. A group the stored catalogue
// doesn't know (it was cleared) counts when any kind's choice could name it.
private fun isChosenFailedGroup(
    id: String,
    kindOf: Map<String, ChoiceKind>,
    catalogue: SourceCatalogue,
    chosen: Map<ChoiceKind, Set<String>>
): Boolean {
    val kind = kindOf[id]
    if (kind != null) {
        return kind !in catalogue.failedKinds && ChoiceRules.isChosen(id, chosen[kind].orEmpty())
    }
    return chosen.any { (each, saved) ->
        each !in catalogue.failedKinds && ChoiceRules.isChosen(id, saved)
    }
}

// Each planned item's album or book, in plan order; an item counts for its first such group.
private fun coversOf(groups: List<ChoiceGroup>, planned: List<SourceItem>): List<PlannedCover> {
    val coverOf = HashMap<String, ChoiceGroup>()
    groups.filter { it.kind.coverBesideItems }.forEach { group ->
        group.itemIds.forEach { coverOf.putIfAbsent(it, group) }
    }
    val byGroup = LinkedHashMap<ChoiceGroup, MutableList<SourceItem>>()
    for (item in planned) {
        val group = coverOf[item.id] ?: continue
        byGroup.getOrPut(group) { ArrayList() }.add(item)
    }
    return byGroup.map { (group, items) -> PlannedCover(group, items) }
}
