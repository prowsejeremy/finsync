package com.jpd.finsync.ui

enum class RefreshStatus { IDLE, RUNNING, FAILED, DONE }

/** What Home shows below its header. */
sealed class HomeLibraryState {
    object Building : HomeLibraryState()
    object Failed : HomeLibraryState()

    /** The six cards' counts: albums, album artists, genres, songs, playlists and books. */
    data class Ready(
        val albumCount: Int,
        val albumArtistCount: Int = 0,
        val genreCount: Int = 0,
        val songCount: Int = 0,
        val playlistCount: Int = 0,
        val bookCount: Int = 0
    ) : HomeLibraryState() {
        /**
         * Nothing downloaded passes the selection, so Home shows the "Choose albums" hint. A
         * playlist's songs count as songs; books count on their own.
         */
        val nothingVisible: Boolean get() = albumCount == 0 && songCount == 0 && bookCount == 0
    }
}

/**
 * With an empty catalogue, Home is building or has failed. A catalogue still empty after a
 * successful refresh (an empty server) shows the cards with 0 rather than spinning forever.
 */
fun homeLibraryStateOf(
    catalogueEmpty: Boolean,
    albumCount: Int,
    refresh: RefreshStatus,
    albumArtistCount: Int = 0,
    genreCount: Int = 0,
    songCount: Int = 0,
    playlistCount: Int = 0,
    bookCount: Int = 0
): HomeLibraryState {
    val ready = HomeLibraryState.Ready(
        albumCount, albumArtistCount, genreCount, songCount, playlistCount, bookCount
    )
    return when {
        !catalogueEmpty -> ready
        refresh == RefreshStatus.FAILED -> HomeLibraryState.Failed
        refresh == RefreshStatus.DONE -> ready
        else -> HomeLibraryState.Building
    }
}
