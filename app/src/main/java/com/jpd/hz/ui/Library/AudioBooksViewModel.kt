package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.hz.library.BookRepository
import com.jpd.hz.library.BookSummary

class AudioBooksViewModel(app: Application) : AndroidViewModel(app) {
    val books: LiveData<List<BookSummary>> = BookRepository(app).books().asLiveData()
}
