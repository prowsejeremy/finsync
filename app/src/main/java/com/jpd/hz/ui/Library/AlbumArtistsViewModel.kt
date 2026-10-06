package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.hz.library.ArtistSummary
import com.jpd.hz.library.LibraryRepository

class AlbumArtistsViewModel(app: Application) : AndroidViewModel(app) {
    val artists: LiveData<List<ArtistSummary>> =
        LibraryRepository(app).albumArtists().asLiveData()
}
