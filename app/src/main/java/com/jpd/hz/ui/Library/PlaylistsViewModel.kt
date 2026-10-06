package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.hz.library.PlaylistRepository
import com.jpd.hz.library.PlaylistSummary

class PlaylistsViewModel(app: Application) : AndroidViewModel(app) {
    val playlists: LiveData<List<PlaylistSummary>> =
        PlaylistRepository(app).playlists().asLiveData()
}
