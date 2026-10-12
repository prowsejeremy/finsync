package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.jpd.hz.library.PlaylistDetail
import com.jpd.hz.library.PlaylistRepository

/** The default factory fills [savedState] from the fragment's arguments. */
class PlaylistViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    private val playlistId: String = checkNotNull(savedState.get<String>(ARG_PLAYLIST_ID)) {
        "PlaylistFragment needs a playlistId"
    }

    /** Null once the playlist is gone or none of it is downloaded. */
    val playlist: LiveData<PlaylistDetail?> =
        PlaylistRepository(app).playlist(playlistId).asLiveData()
}
