package com.jpd.hz.adapter

import android.content.Context

private const val PREFS = "settings"
private const val KEY = "adapter_folders"

/**
 * The `adapter_folders` setting. Sign-out leaves it, so signing back in to a server reuses its
 * folder (spec "Saved settings").
 */
class AdapterFolderStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<AdapterFolder> = AdapterFolders.parse(prefs.getString(KEY, null))

    fun pathFor(adapter: String, serverId: String): String? =
        AdapterFolders.find(all(), adapter, serverId)?.path

    fun save(folder: AdapterFolder) {
        val folders = AdapterFolders.withFolder(all(), folder)
        prefs.edit().putString(KEY, AdapterFolders.format(folders)).apply()
    }
}
