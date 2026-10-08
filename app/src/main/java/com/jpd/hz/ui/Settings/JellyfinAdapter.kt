package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.asFlow
import com.jpd.hz.library.LibraryFolderStore
import com.jpd.hz.sync.PLATFORM
import com.jpd.hz.sync.SyncEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Jellyfin as Settings → Adapters shows it, built on [MainViewModel]'s sign-in and Sync card
 * state, which the Jellyfin page shares.
 */
class JellyfinAdapter(
    private val app: Application,
    private val viewModel: MainViewModel
) : Adapter {

    override val name: String = PLATFORM

    override val status: Flow<AdapterStatus> =
        combine(viewModel.config.asFlow(), viewModel.uiState.asFlow(), ::adapterStatusOf)

    // "kurage": where Jellyfin syncs, named from the Library folder (T3).
    override fun folder(): String? {
        val config = viewModel.config.value ?: return null
        val folder = SyncEngine.getSyncDirectory(app, config).path
        return LibraryFolderStore(app).nameInLibrary(folder)
    }

    override fun signOut() = viewModel.signOut()
}
