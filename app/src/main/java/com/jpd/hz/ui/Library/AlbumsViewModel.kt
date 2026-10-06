package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.hz.library.AlbumSummary
import com.jpd.hz.library.LibraryRepository

class AlbumsViewModel(app: Application) : AndroidViewModel(app) {
    val albums: LiveData<List<AlbumSummary>> = LibraryRepository(app).albums().asLiveData()
}
