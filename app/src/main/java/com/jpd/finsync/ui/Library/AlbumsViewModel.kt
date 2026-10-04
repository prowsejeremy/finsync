package com.jpd.finsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.finsync.library.AlbumSummary
import com.jpd.finsync.library.LibraryRepository

class AlbumsViewModel(app: Application) : AndroidViewModel(app) {
    val albums: LiveData<List<AlbumSummary>> = LibraryRepository(app).albums().asLiveData()
}
