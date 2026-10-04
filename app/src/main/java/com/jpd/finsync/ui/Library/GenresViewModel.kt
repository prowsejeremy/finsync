package com.jpd.finsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.finsync.library.GenreSummary
import com.jpd.finsync.library.LibraryRepository

class GenresViewModel(app: Application) : AndroidViewModel(app) {
    val genres: LiveData<List<GenreSummary>> = LibraryRepository(app).genres().asLiveData()
}
