package com.jpd.hz.adapter.choices

import android.content.Context
import com.jpd.hz.adapter.ChoiceKind

private const val PREFS = "settings"
private const val KEY_PREFIX = "choices"

/**
 * Each connection's choices, as `choices:<connectionId>:<kind>` string sets of group IDs or
 * [ALL] (spec "Saved settings"). Nothing is kept from before the harness: a new install's
 * connections start with nothing chosen.
 */
class ChoiceStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // A copy: the set getStringSet returns mustn't be modified or kept (SharedPreferences docs).
    fun chosen(connectionId: String, kind: ChoiceKind): Set<String> =
        prefs.getStringSet(keyOf(connectionId, kind), emptySet())?.toSet() ?: emptySet()

    fun setChosen(connectionId: String, kind: ChoiceKind, ids: Set<String>) {
        prefs.edit().putStringSet(keyOf(connectionId, kind), ids.toSet()).apply()
    }

    /** Every kind's choice, for a run or a count. */
    fun chosen(connectionId: String, kinds: List<ChoiceKind>): Map<ChoiceKind, Set<String>> =
        kinds.associateWith { chosen(connectionId, it) }

    private fun keyOf(connectionId: String, kind: ChoiceKind) =
        "$KEY_PREFIX:$connectionId:${kind.key}"
}
