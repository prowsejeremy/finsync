package com.jpd.finsync.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.finsync.library.BookRepository
import com.jpd.finsync.library.LibraryRepository
import com.jpd.finsync.library.PlaylistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "LibraryViewModel"

/** Home's library state. Builds the catalogue on first open when it's empty, without a sync. */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val library = LibraryRepository(app)
    private val playlists = PlaylistRepository(app)
    private val books = BookRepository(app)
    private val refreshStatus = MutableStateFlow(RefreshStatus.IDLE)

    val homeState: LiveData<HomeLibraryState> = combine(
        library.isCatalogueEmpty(),
        categoryCounts(),
        refreshStatus
    ) { empty, counts, status ->
        homeLibraryStateOf(
            catalogueEmpty = empty,
            albumCount = counts.albums,
            refresh = status,
            albumArtistCount = counts.albumArtists,
            genreCount = counts.genres,
            songCount = counts.songs,
            playlistCount = counts.playlists,
            bookCount = counts.books
        )
    }.asLiveData()

    init {
        refreshIfEmpty()
    }

    fun retry() = refreshIfEmpty()

    // combine takes at most five typed Flows, so playlists and books travel as a pair.
    private fun categoryCounts(): Flow<CategoryCounts> = combine(
        library.albumCount(),
        library.albumArtistCount(),
        library.genreCount(),
        library.songCount(),
        combine(playlists.playlistCount(), books.bookCount()) { playlistCount, bookCount ->
            playlistCount to bookCount
        }
    ) { albums, albumArtists, genres, songs, (playlistCount, bookCount) ->
        CategoryCounts(albums, albumArtists, genres, songs, playlistCount, bookCount)
    }

    private fun refreshIfEmpty() {
        if (refreshStatus.value == RefreshStatus.RUNNING) return
        // Set before launching, so a second tap can't start a second refresh.
        refreshStatus.value = RefreshStatus.RUNNING
        viewModelScope.launch {
            // Room and bad server data throw too, not only network errors.
            val succeeded = try {
                !library.isCatalogueEmpty().first() || library.refreshCatalogue()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't build catalogue", e)
                false
            }
            refreshStatus.value = if (succeeded) RefreshStatus.DONE else RefreshStatus.FAILED
        }
    }

    private data class CategoryCounts(
        val albums: Int,
        val albumArtists: Int,
        val genres: Int,
        val songs: Int,
        val playlists: Int,
        val books: Int
    )
}
