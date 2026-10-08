package com.jpd.hz.library

import android.content.Context
import android.content.SharedPreferences

private const val SETTINGS_PREFS = "settings"
private const val SELECTED_ALBUMS_KEY = "selected_albums"
private const val SELECTED_PLAYLISTS_KEY = "selected_playlists"
private const val SELECTED_BOOKS_KEY = "selected_books"
private val SELECTION_KEYS = listOf(SELECTED_ALBUMS_KEY, SELECTED_PLAYLISTS_KEY, SELECTED_BOOKS_KEY)
// The server whose selections the keys above hold. Another server's wait under "<key>:<its ID>".
private const val SELECTIONS_SERVER_KEY = "selections_server"

/**
 * The playlist and book selections, beside "selected_albums" in the settings prefs. Unlike
 * albums, an empty set means none, so a fresh install downloads no playlists or books (spec).
 * All three belong to one server at a time ([useFor]).
 */
class SyncSelections(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)

    fun playlistIds(): Set<String> = read(SELECTED_PLAYLISTS_KEY)

    fun setPlaylistIds(ids: Set<String>) = write(SELECTED_PLAYLISTS_KEY, ids)

    fun bookIds(): Set<String> = read(SELECTED_BOOKS_KEY)

    fun setBookIds(ids: Set<String>) = write(SELECTED_BOOKS_KEY, ids)

    /**
     * Makes the selections [serverId]'s (T4): at sign-in and at each sync's start. Another
     * server's are put aside, and [serverId]'s come back, or start empty for a server never
     * signed in to. Selections are server IDs, so one server's in force for another would plan
     * none of its albums, and the sync's cleanup would delete its files. Selections saved before
     * T4 belong to the first server to ask.
     */
    fun useFor(serverId: String) {
        val owner = prefs.getString(SELECTIONS_SERVER_KEY, null)
        if (owner == serverId) return
        val edit = prefs.edit()
        if (owner != null) {
            SELECTION_KEYS.forEach { key ->
                move(edit, from = key, to = "$key:$owner")
                move(edit, from = "$key:$serverId", to = key)
            }
        }
        edit.putString(SELECTIONS_SERVER_KEY, serverId).commit()
    }

    // A key that isn't there moves as "not there", so an empty album selection, which means
    // every album, never turns into one that's missing or the other way round.
    private fun move(edit: SharedPreferences.Editor, from: String, to: String) {
        if (prefs.contains(from)) edit.putStringSet(to, read(from)) else edit.remove(to)
        edit.remove(from)
    }

    // A copy: the set getStringSet returns mustn't be modified or kept (SharedPreferences docs).
    private fun read(key: String): Set<String> =
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

    private fun write(key: String, ids: Set<String>) {
        prefs.edit().putStringSet(key, ids.toSet()).apply()
    }
}
