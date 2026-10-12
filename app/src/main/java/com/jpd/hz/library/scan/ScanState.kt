package com.jpd.hz.library.scan

/** One finished pass, for Settings → Library's scan card. */
data class ScanResult(
    val songs: Int,
    val books: Int,
    /** Playlists with at least one song in the library. */
    val playlists: Int,
    /** Files neither TagLib nor BASS could open. */
    val unreadable: Int,
    val finishedAt: Long
)

/** The scanner's state (spec "Scanning"). */
sealed class ScanState {
    /** Not scanning. [lastResult] is null until a pass finishes in this process. */
    data class Idle(val lastResult: ScanResult?) : ScanState()

    /** [done] of [total] new or changed files read. Both are 0 while the folder is walked. */
    data class Scanning(val done: Int, val total: Int) : ScanState()

    /** The last pass couldn't list [folder], so the library was kept as it was. */
    data class Failed(val folder: String) : ScanState()
}
