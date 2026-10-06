package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import com.jpd.hz.library.AlbumDetail
import com.jpd.hz.library.LibraryRepository

/** The default factory fills [savedState] from the fragment's arguments. */
class AlbumViewModel(app: Application, savedState: SavedStateHandle) : AndroidViewModel(app) {

    private val albumId: String =
        checkNotNull(savedState.get<String>(ARG_ALBUM_ID)) { "AlbumFragment needs an albumId" }

    /** Null once none of the album is downloaded, e.g. after a sync removes it. */
    val album: LiveData<AlbumDetail?> = LibraryRepository(app).album(albumId).asLiveData()
}
