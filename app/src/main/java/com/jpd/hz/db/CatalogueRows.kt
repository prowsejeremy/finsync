package com.jpd.hz.db

/** A catalogue track's ID and album (null for none), for the Sync card's counts. */
data class TrackAlbumRow(
    val itemId: String,
    val albumId: String?
)

/** A playlist and how many audio entries it has on the server, for Playlists to Sync. */
data class PlaylistChoiceRow(
    val playlistId: String,
    val name: String,
    val entryCount: Int
)
