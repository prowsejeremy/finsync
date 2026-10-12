package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.hz.library.GenreSummary
import com.jpd.hz.library.LibraryRepository

class GenresViewModel(app: Application) : AndroidViewModel(app) {
    val genres: LiveData<List<GenreSummary>> = LibraryRepository(app).genres().asLiveData()
}
