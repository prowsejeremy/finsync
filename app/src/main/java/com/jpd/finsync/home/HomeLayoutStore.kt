package com.jpd.finsync.home

import android.content.Context

private const val SETTINGS_PREFS = "settings"
private const val HOME_ORDER_KEY = "home_order"
private const val HOME_HIDDEN_KEY = "home_hidden"

/** Home's saved layout, in the settings prefs (spec "Saved settings"). */
class HomeLayoutStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    fun load(): HomeLayout = homeLayoutOf(
        prefs.getString(HOME_ORDER_KEY, null),
        prefs.getStringSet(HOME_HIDDEN_KEY, null)
    )

    // Both keys at once, so the order and the hidden set always describe the same layout.
    fun save(layout: HomeLayout) {
        prefs.edit()
            .putString(HOME_ORDER_KEY, layout.orderValue())
            .putStringSet(HOME_HIDDEN_KEY, layout.hidden.mapTo(HashSet()) { it.key })
            .apply()
    }
}
