package com.jpd.hz.ui

import com.jpd.hz.model.ServerConfig
import kotlinx.coroutines.flow.Flow

/**
 * An adapter as Settings → Adapters lists it (spec "Settings → Adapters", D12): its name, its
 * folder, its status and sign-out. There's one, Jellyfin, and no registry until a second exists.
 */
interface Adapter {
    /** The platform's name: "Jellyfin". */
    val name: String

    /** Signed out, or signed in with its sync's state; it updates live. */
    val status: Flow<AdapterStatus>

    /** Its folder's name in the Library folder, such as "kurage"; null while signed out. */
    fun folder(): String?

    /** Clears its sign-in and its server's data, and keeps its files (spec "Signing out"). */
    fun signOut()
}

/** What an adapter's row shows. */
sealed class AdapterStatus {
    object SignedOut : AdapterStatus()

    data class SignedIn(val display: SyncDisplay) : AdapterStatus()
}

/** Jellyfin's status from the sign-in and the Sync card's state (spec "Settings → Adapters"). */
fun adapterStatusOf(config: ServerConfig?, state: MainViewModel.UiState): AdapterStatus =
    if (config == null) AdapterStatus.SignedOut else AdapterStatus.SignedIn(SyncDisplay.from(state))
