package com.jpd.hz.ui

import com.jpd.hz.adapter.Connection

/** What a platform's row shows: signed out, or signed in with its Sync card's display. */
sealed class AdapterStatus {
    object SignedOut : AdapterStatus()

    data class SignedIn(val display: SyncDisplay) : AdapterStatus()
}

/** A platform's status from its connection, if any, and that connection's state. */
fun adapterStatusOf(connection: Connection?, state: ConnectionUiState): AdapterStatus =
    if (connection == null) {
        AdapterStatus.SignedOut
    } else {
        AdapterStatus.SignedIn(SyncDisplay.from(state))
    }
