package com.jpd.finsync.library

import android.content.Context
import android.util.Log
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.auth.Result
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "LibraryRepository"
private const val SETTINGS_PREFS = "settings"
private const val SELECTED_ALBUMS_KEY = "selected_albums"

/**
 * The one place screens and the playback service read library data from (overview rule 2).
 * Queries are Room Flows, so screens update when a sync writes.
 */
class LibraryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val catalogueDao = SyncDatabase.getInstance(appContext).catalogueDao()

    /** Visible albums by name: at least one downloaded track, and included in the selection. */
    fun albums(): Flow<List<AlbumSummary>> =
        catalogueDao.observeAlbumSummaries()
            .conflate()
            .map { rows ->
                // Read on each emission, so a selection change shows after the next DB change.
                val selectedIds = selectedAlbumIds()
                rows.filter { isAlbumSelected(it.albumId, selectedIds) }.map { row ->
                    AlbumSummary(
                        albumId = row.albumId,
                        name = row.name,
                        albumArtist = row.albumArtist,
                        year = row.year,
                        downloadedTrackCount = row.downloadedCount,
                        artworkPath = chooseArtwork(
                            row.storedArtworkPath, row.firstTrackPath, ::fileExists
                        )
                    )
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun albumCount(): Flow<Int> = albums().map { it.size }.distinctUntilChanged()

    fun isCatalogueEmpty(): Flow<Boolean> =
        catalogueDao.observeTrackCount().map { it == 0 }.distinctUntilChanged()

    /** The album and its downloaded tracks, or null once none of it is downloaded. */
    fun album(albumId: String): Flow<AlbumDetail?> =
        combine(
            catalogueDao.observeAlbumHeader(albumId),
            catalogueDao.observeDownloadedTracks(albumId)
        ) { header, rows ->
            if (header == null || rows.isEmpty()) {
                null
            } else {
                AlbumDetail(
                    albumId = header.albumId,
                    name = header.name,
                    albumArtist = header.albumArtist,
                    year = header.year,
                    artworkPath = chooseArtwork(
                        header.storedArtworkPath, rows.first().localPath, ::fileExists
                    ),
                    tracks = rows.map { it.track }
                )
            }
        }.flowOn(Dispatchers.IO)

    /** The catalogue and sync rows the resolver needs, or null if the track isn't downloaded. */
    suspend fun playableTrack(itemId: String): PlayableSource? = withContext(Dispatchers.IO) {
        catalogueDao.playableTrack(itemId)?.let { row ->
            PlayableSource(
                track = row.track,
                localPath = row.localPath,
                albumName = row.albumName,
                artworkPath = chooseArtwork(row.storedArtworkPath, row.localPath, ::fileExists)
            )
        }
    }

    /** Fetches the server's audio items and replaces the catalogue, without downloading. */
    suspend fun refreshCatalogue(): Boolean {
        val jellyfin = JellyfinRepository(appContext)
        val config = jellyfin.getSavedConfig() ?: return false
        val result = jellyfin.getAllAudioItems(config)
        // getAllAudioItems turns cancellation into Result.Error (known item 10), so rethrow here.
        currentCoroutineContext().ensureActive()
        return when (result) {
            is Result.Success -> {
                writeCatalogue(result.data)
                true
            }
            is Result.Error -> {
                Log.w(TAG, "Catalogue refresh failed: ${result.message}")
                false
            }
        }
    }

    suspend fun writeCatalogue(items: List<MediaItem>) {
        val catalogue = withContext(Dispatchers.Default) { catalogueFrom(items) }
        catalogueDao.replaceCatalogue(catalogue.albums, catalogue.tracks)
    }

    suspend fun clearCatalogue() = catalogueDao.clearCatalogue()

    private fun selectedAlbumIds(): Set<String> =
        appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getStringSet(SELECTED_ALBUMS_KEY, emptySet()) ?: emptySet()

    private fun fileExists(path: String): Boolean = File(path).exists()
}
