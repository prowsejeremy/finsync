package com.jpd.hz.sync

import android.content.Context
import android.util.Log
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.auth.Result
import com.jpd.hz.db.CatalogueDao
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.library.PlaylistBookRows
import com.jpd.hz.library.catalogueBooksFrom
import com.jpd.hz.library.catalogueTracksFrom
import com.jpd.hz.library.keepFailedParts
import com.jpd.hz.library.playlistRowsFrom
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.ServerCatalogue
import com.jpd.hz.model.ServerPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val TAG = "JellyfinCatalogue"

/** A playlist in Playlists to Sync, with its audio entry count on the server. */
data class PlaylistChoice(
    val playlistId: String,
    val name: String,
    val songCount: Int
)

/** A book in Books to Sync, with its file size. */
data class BookChoice(
    val bookId: String,
    val name: String,
    val author: String?,
    val sizeBytes: Long?
)

/**
 * The Jellyfin adapter's copy of the server's catalogue (T3; moved from LibraryRepository): what
 * sync plans from, what the Sync card counts, and what the choice screens list.
 */
class JellyfinCatalogue internal constructor(
    context: Context,
    private val dao: CatalogueDao
) {

    constructor(context: Context) : this(
        context,
        SyncDatabase.getInstance(context.applicationContext).catalogueDao()
    )

    private val appContext = context.applicationContext

    /** Fetches the server's catalogue and replaces it, without downloading. */
    suspend fun refresh(): Boolean {
        val jellyfin = JellyfinRepository(appContext)
        val config = jellyfin.getSavedConfig() ?: return false
        val result = jellyfin.getServerCatalogue(config)
        // getServerCatalogue turns cancellation into Result.Error (known item 10), so rethrow.
        currentCoroutineContext().ensureActive()
        return when (result) {
            is Result.Success -> {
                write(result.data)
                true
            }
            is Result.Error -> {
                Log.w(TAG, "Catalogue refresh failed: ${result.message}")
                false
            }
        }
    }

    /**
     * Replaces the catalogue with one fetch, in one transaction. A part whose fetch failed keeps
     * its previous rows (decision 3). Returns the playlist and book rows written, which sync
     * plans from.
     */
    suspend fun write(catalogue: ServerCatalogue): PlaylistBookRows {
        // Read just before the swap; a refresh racing a sync writes the same server data.
        val previous = PlaylistBookRows(
            playlists = dao.allPlaylists(),
            playlistItems = dao.allPlaylistItems(),
            books = dao.allBooks()
        )
        val fresh = withContext(Dispatchers.Default) {
            freshRows(catalogue.playlists, catalogue.books)
        }
        val rows = keepFailedParts(fresh, previous, catalogue)
        writeRows(catalogue.audio, rows)
        return rows
    }

    /** Writes the given items, playlists and books, none failed: for tests. */
    suspend fun write(
        items: List<MediaItem>,
        playlists: List<ServerPlaylist> = emptyList(),
        books: List<MediaItem> = emptyList()
    ) {
        writeRows(items, freshRows(playlists, books))
    }

    /** Logout: another server's catalogue must never show. */
    suspend fun clear() = dao.clearCatalogue()

    suspend fun isEmpty(): Boolean = dao.trackCount() == 0

    /** Every audio playlist on the server, A–Z, for Playlists to Sync. */
    fun playlistChoices(): Flow<List<PlaylistChoice>> =
        dao.observePlaylistChoices()
            .map { rows -> rows.map { PlaylistChoice(it.playlistId, it.name, it.entryCount) } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** Every audiobook on the server, A–Z, for Books to Sync. */
    fun bookChoices(): Flow<List<BookChoice>> =
        dao.observeBookChoices()
            .map { books -> books.map { BookChoice(it.bookId, it.name, it.author, it.size) } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    private fun freshRows(playlists: List<ServerPlaylist>, books: List<MediaItem>) =
        playlistRowsFrom(playlists).copy(books = catalogueBooksFrom(books))

    private suspend fun writeRows(items: List<MediaItem>, rows: PlaylistBookRows) {
        val tracks = withContext(Dispatchers.Default) { catalogueTracksFrom(items) }
        dao.replaceCatalogue(tracks, rows.playlists, rows.playlistItems, rows.books)
    }
}
