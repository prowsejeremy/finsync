package com.jpd.finsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.finsync.library.BookRepository
import com.jpd.finsync.library.BookSummary

class AudioBooksViewModel(app: Application) : AndroidViewModel(app) {
    val books: LiveData<List<BookSummary>> = BookRepository(app).books().asLiveData()
}
