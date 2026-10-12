package com.jpd.hz.home

/** Home's categories, in the order Home had before it could be changed. [key] is saved. */
enum class HomeCategory(val key: String) {
    ALBUMS("albums"),
    ALBUM_ARTISTS("album_artists"),
    GENRES("genres"),
    SONGS("songs"),
    PLAYLISTS("playlists"),
    AUDIO_BOOKS("audio_books");

    companion object {
        /** The category saved as [key], or null for a key this version doesn't know. */
        fun fromKey(key: String): HomeCategory? = entries.firstOrNull { it.key == key }
    }
}
