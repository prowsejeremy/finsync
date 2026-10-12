package com.jpd.hz.ui

import com.jpd.hz.library.scan.ScanState

/** What Home shows below its header. */
sealed class HomeLibraryState {
    /** The library is empty, and the first scan is running or hasn't finished yet. */
    object Building : HomeLibraryState()

    /** The library is empty, and the last scan couldn't read the Library folder. */
    object Failed : HomeLibraryState()

    /** The six cards' counts: albums, album artists, genres, songs, playlists and books. */
    data class Ready(
        val albumCount: Int,
        val albumArtistCount: Int = 0,
        val genreCount: Int = 0,
        val songCount: Int = 0,
        val playlistCount: Int = 0,
        val bookCount: Int = 0,
        /** The last scan couldn't read the folder, so the cards show the library as it was. */
        val scanFailed: Boolean = false
    ) : HomeLibraryState() {
        /** Nothing in the library, so Home says "No music found in <folder>." */
        val isEmpty: Boolean get() = albumCount == 0 && songCount == 0 && bookCount == 0
    }
}

/**
 * With an empty library, Home shows the scan's progress or its failure (spec "Home's states").
 * Once a scan has finished, an empty library shows the cards with 0 and the empty state rather
 * than spinning forever.
 */
fun homeLibraryStateOf(
    libraryEmpty: Boolean,
    scan: ScanState,
    albumCount: Int,
    albumArtistCount: Int = 0,
    genreCount: Int = 0,
    songCount: Int = 0,
    playlistCount: Int = 0,
    bookCount: Int = 0
): HomeLibraryState {
    val ready = HomeLibraryState.Ready(
        albumCount,
        albumArtistCount,
        genreCount,
        songCount,
        playlistCount,
        bookCount,
        scanFailed = scan is ScanState.Failed
    )
    return when {
        !libraryEmpty -> ready
        scan is ScanState.Failed -> HomeLibraryState.Failed
        scan is ScanState.Idle && scan.lastResult != null -> ready
        else -> HomeLibraryState.Building
    }
}
