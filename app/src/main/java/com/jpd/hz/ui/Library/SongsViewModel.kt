package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.hz.library.LibraryRepository
import com.jpd.hz.library.SongRow

class SongsViewModel(app: Application) : AndroidViewModel(app) {
    val songs: LiveData<List<SongRow>> = LibraryRepository(app).songs().asLiveData()
}
