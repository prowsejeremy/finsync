package com.jpd.finsync.library

import android.content.Context

private const val SETTINGS_PREFS = "settings"
private const val SELECTED_PLAYLISTS_KEY = "selected_playlists"
private const val SELECTED_BOOKS_KEY = "selected_books"

/**
 * The playlist and book selections, beside "selected_albums" in the settings prefs. Unlike
 * albums, an empty set means none, so a fresh install downloads no playlists or books (spec).
 */
class SyncSelections(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    fun playlistIds(): Set<String> = read(SELECTED_PLAYLISTS_KEY)

    fun setPlaylistIds(ids: Set<String>) = write(SELECTED_PLAYLISTS_KEY, ids)

    fun bookIds(): Set<String> = read(SELECTED_BOOKS_KEY)

    // A copy: the set getStringSet returns mustn't be modified or kept (SharedPreferences docs).
    private fun read(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun write(key: String, ids: Set<String>) {
        prefs.edit().putStringSet(key, ids.toSet()).apply()
    }
}
