package com.jpd.hz.adapter.choices

import com.jpd.hz.adapter.ChoiceKind

/** Saved in place of IDs when every group of a kind is chosen, including ones added later. */
const val ALL = "all"

/**
 * The choice rules for every kind and every adapter (spec H4), with no Android imports: an empty
 * selection means none, and [ALL] means every group.
 */
object ChoiceRules {

    fun selectsEvery(saved: Set<String>): Boolean = ALL in saved

    fun isChosen(groupId: String, saved: Set<String>): Boolean =
        selectsEvery(saved) || groupId in saved

    /** True when nothing of any kind is chosen: the connection doesn't run (spec H4). */
    fun nothingChosen(saved: Collection<Set<String>>): Boolean = saved.all { it.isEmpty() }

    /**
     * What a choice screen saves when Select is pressed, from its [listed] groups, those
     * [checked], and what was [saved] before. Nothing is saved before a list has loaded (spec
     * "Sign-in health", decision 5). A kind whose Select all saves [ALL] saves it when every
     * listed group is ticked. Saved groups the screen didn't list (a playlist whose entries
     * didn't load, or before a refresh) are kept, as Playlists and Books kept them before the
     * harness, so a screen never drops a choice it didn't show.
     */
    fun idsToSave(
        kind: ChoiceKind,
        checked: Set<String>,
        listed: List<String>,
        saved: Set<String>
    ): Set<String>? = when {
        listed.isEmpty() -> null
        kind.selectAllSavesAll && checked.containsAll(listed) -> setOf(ALL)
        else -> checked + (saved - listed.toSet() - ALL)
    }

    /** The groups to tick on opening a choice screen, from what's saved. */
    fun tickedOf(saved: Set<String>, listed: List<String>): Set<String> =
        if (selectsEvery(saved)) listed.toSet() else listed.filterTo(HashSet()) { it in saved }
}
