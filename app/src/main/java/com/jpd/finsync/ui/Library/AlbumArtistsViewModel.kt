package com.jpd.finsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.finsync.library.ArtistSummary
import com.jpd.finsync.library.LibraryRepository

class AlbumArtistsViewModel(app: Application) : AndroidViewModel(app) {
    val artists: LiveData<List<ArtistSummary>> =
        LibraryRepository(app).albumArtists().asLiveData()
}
