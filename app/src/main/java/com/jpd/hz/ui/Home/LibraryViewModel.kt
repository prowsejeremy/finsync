package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.jpd.hz.library.BookRepository
import com.jpd.hz.library.LibraryFolderStore
import com.jpd.hz.library.LibraryRepository
import com.jpd.hz.library.PlaylistRepository
import com.jpd.hz.library.scan.LibraryScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Home's library state, from the library and the scanner. The app-open scan (MainViewModel)
 * builds an empty library, so Home starts none of its own.
 */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val library = LibraryRepository(app)
    private val playlists = PlaylistRepository(app)
    private val books = BookRepository(app)
    private val scanner = LibraryScanner.get(app)
    private val folders = LibraryFolderStore(app)

    val homeState: LiveData<HomeLibraryState> = combine(
        library.isLibraryEmpty(),
        categoryCounts(),
        scanner.state
    ) { empty, counts, scan ->
        homeLibraryStateOf(
            libraryEmpty = empty,
            scan = scan,
            albumCount = counts.albums,
            albumArtistCount = counts.albumArtists,
            genreCount = counts.genres,
            songCount = counts.songs,
            playlistCount = counts.playlists,
            bookCount = counts.books
        )
    }.asLiveData()

    /** The Library folder as Home names it, such as "Media/hz". */
    fun folderLabel(): String = folders.displayPath()

    /** After "Can't read …": another scan, which also retries unreadable files. */
    fun retry() = scanner.rescan()

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

    private data class CategoryCounts(
        val albums: Int,
        val albumArtists: Int,
        val genres: Int,
        val songs: Int,
        val playlists: Int,
        val books: Int
    )
}
