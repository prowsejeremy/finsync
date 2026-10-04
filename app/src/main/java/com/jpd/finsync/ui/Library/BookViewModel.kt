package com.jpd.finsync.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.jpd.finsync.library.BookDetail
import com.jpd.finsync.library.BookRepository

/** The default factory fills [savedState] from the fragment's arguments. */
class BookViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    val bookId: String = checkNotNull(savedState.get<String>(ARG_BOOK_ID)) {
        "BookFragment needs a bookId"
    }

    /** Null once the book isn't downloaded. */
    val book: LiveData<BookDetail?> = BookRepository(app).book(bookId).asLiveData()
}
