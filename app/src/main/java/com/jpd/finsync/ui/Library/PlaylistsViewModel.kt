package com.jpd.finsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.finsync.library.PlaylistRepository
import com.jpd.finsync.library.PlaylistSummary

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    val playlists: LiveData<List<PlaylistSummary>> =
        PlaylistRepository(app).playlists().asLiveData()
}
