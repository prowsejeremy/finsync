package com.jpd.finsync.ui

enum class RefreshStatus { IDLE, RUNNING, FAILED, DONE }

/** What Home shows below its header. */
sealed class HomeLibraryState {
    object Building : HomeLibraryState()
    object Failed : HomeLibraryState()
    data class Ready(val albumCount: Int) : HomeLibraryState()
}

/**
 * With an empty catalogue, Home is building or has failed. A catalogue still empty after a
 * successful refresh (an empty server) shows the Albums card with 0 rather than spinning forever.
 */
fun homeLibraryStateOf(
    catalogueEmpty: Boolean,
    albumCount: Int,
    refresh: RefreshStatus
): HomeLibraryState = when {
    !catalogueEmpty -> HomeLibraryState.Ready(albumCount)
    refresh == RefreshStatus.FAILED -> HomeLibraryState.Failed
    refresh == RefreshStatus.DONE -> HomeLibraryState.Ready(albumCount)
    else -> HomeLibraryState.Building
}
