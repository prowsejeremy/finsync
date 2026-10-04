package com.jpd.finsync.library

import android.content.Context
import android.util.Log
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.auth.Result
import com.jpd.finsync.db.AlbumSummaryRow
import com.jpd.finsync.db.CatalogueDao
import com.jpd.finsync.db.SongTrackRow
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
private const val ARTIST_SEPARATOR = ", "
// SQLite before 3.32 (Android before 11) allows 999 bound parameters in one statement.
private const val MAX_IDS_PER_QUERY = 900

/**
 * The one place screens and the playback service read library data from (overview rule 2).
 * Queries are Room Flows, so screens update when a sync writes. SQL does the joins; Kotlin
 * applies the album selection, which lives in SharedPreferences.
 */
class LibraryRepository internal constructor(
    context: Context,
    private val catalogueDao: CatalogueDao
) {

    constructor(context: Context) : this(
        context,
        SyncDatabase.getInstance(context.applicationContext).catalogueDao()
    )

    private val appContext = context.applicationContext

    /** Visible albums by name: at least one downloaded track, and included in the selection. */
    fun albums(): Flow<List<AlbumSummary>> =
        catalogueDao.observeAlbumSummaries()
            .conflate()
            .map { rows ->
                // Read on each emission, so a selection change shows after the next DB change.
                val selectedIds = selectedAlbumIds()
                rows.filter { isAlbumSelected(it.albumId, selectedIds) }.map(::albumSummaryOf)
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun albumCount(): Flow<Int> = albums().map { it.size }.distinctUntilChanged()

    /** Artists who are album artist of at least one visible album, A–Z ignoring case. */
    fun albumArtists(): Flow<List<ArtistSummary>> =
        catalogueDao.observeAlbumArtistCredits()
            .conflate()
            .map { credits ->
                albumArtistSummaries(credits, selectedAlbumIds()) { artistId ->
                    ArtistPhotos.pathIfExists(appContext, artistId)
                }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    // Home's count skips the photo file checks.
    fun albumArtistCount(): Flow<Int> =
        catalogueDao.observeAlbumArtistCredits()
            .conflate()
            .map { credits -> albumArtistSummaries(credits, selectedAlbumIds()) { null }.size }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** Genres with at least one visible track, A–Z ignoring case. */
    fun genres(): Flow<List<GenreSummary>> =
        catalogueDao.observeGenreTags()
            .conflate()
            .map { tags -> genreSummaries(tags, selectedAlbumIds()) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun genreCount(): Flow<Int> = genres().map { it.size }.distinctUntilChanged()

    /** Every visible track, A–Z by title ignoring case. */
    fun songs(): Flow<List<SongRow>> =
        catalogueDao.observeSongs()
            .conflate()
            .map { rows -> visibleSongs(rows, selectedAlbumIds()) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    // Home's count reads only album IDs, not whole tracks.
    fun songCount(): Flow<Int> =
        catalogueDao.observeDownloadedTrackAlbumIds()
            .conflate()
            .map { albumIds ->
                val selectedIds = selectedAlbumIds()
                albumIds.count { isTrackVisible(it, selectedIds) }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

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

    /**
     * The artist's page: visible albums where they're an album artist, and All songs, which adds
     * visible tracks elsewhere that credit them. Null once none of it is visible.
     */
    fun artist(artistId: String): Flow<GroupDetail?> =
        combine(
            catalogueDao.observeArtistName(artistId),
            catalogueDao.observeArtistAlbums(artistId),
            catalogueDao.observeArtistSongs(artistId)
        ) { name, albumRows, songRows ->
            val photoPath = ArtistPhotos.pathIfExists(appContext, artistId)
            groupOf(artistId, name, photoPath, albumRows, songRows)
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** The genre's page: albums with a visible track in the genre, and those tracks. */
    fun genre(genreId: String): Flow<GroupDetail?> =
        combine(
            catalogueDao.observeGenreName(genreId),
            catalogueDao.observeGenreAlbums(genreId),
            catalogueDao.observeGenreSongs(genreId)
        ) { name, albumRows, songRows -> groupOf(genreId, name, null, albumRows, songRows) }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /**
     * The catalogue and sync rows the resolver needs, one per ID in the order asked, null where a
     * track isn't downloaded. A long queue takes a few chunked queries, not one per track.
     */
    suspend fun playableTracks(itemIds: List<String>): List<PlayableSource?> =
        withContext(Dispatchers.IO) {
            val rows = itemIds.distinct()
                .chunked(MAX_IDS_PER_QUERY)
                .flatMap { chunk -> catalogueDao.playableTracks(chunk) }
                .associateBy { it.track.itemId }
            val artwork = AlbumArtworkCache(::fileExists)
            itemIds.map { id ->
                rows[id]?.let { row ->
                    PlayableSource(
                        track = row.track,
                        localPath = row.localPath,
                        albumName = row.albumName,
                        artworkPath = artwork.artworkFor(
                            row.track.albumId, row.storedArtworkPath, row.localPath
                        )
                    )
                }
            }
        }

    /** Sync's artist photos: album artists of albums with a downloaded track, any selection. */
    suspend fun downloadedAlbumArtistIds(): Set<String> =
        catalogueDao.downloadedAlbumArtistIds().toSet()

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
        catalogueDao.replaceCatalogue(
            catalogue.albums,
            catalogue.tracks,
            catalogue.artists,
            catalogue.albumArtists,
            catalogue.trackArtists,
            catalogue.genres,
            catalogue.trackGenres
        )
    }

    suspend fun clearCatalogue() = catalogueDao.clearCatalogue()

    /** Logout: another server's artists must never show. */
    suspend fun deleteArtistPhotos() = withContext(Dispatchers.IO) {
        ArtistPhotos.deleteAll(appContext)
    }

    private fun groupOf(
        id: String,
        name: String?,
        photoPath: String?,
        albumRows: List<AlbumSummaryRow>,
        songRows: List<SongTrackRow>
    ): GroupDetail? {
        val selectedIds = selectedAlbumIds()
        val albums = albumRows
            .filter { isAlbumSelected(it.albumId, selectedIds) }
            .map(::albumSummaryOf)
        return groupDetailOf(id, name, photoPath, albums, visibleSongs(songRows, selectedIds))
    }

    private fun visibleSongs(rows: List<SongTrackRow>, selectedIds: Set<String>): List<SongRow> {
        val artwork = AlbumArtworkCache(::fileExists)
        return rows.filter { isTrackVisible(it.track.albumId, selectedIds) }.map { row ->
            val track = row.track
            SongRow(
                itemId = track.itemId,
                title = track.name,
                artists = track.artistNames.takeIf { it.isNotEmpty() }
                    ?.joinToString(ARTIST_SEPARATOR)
                    ?: track.albumArtist,
                albumId = track.albumId,
                albumName = row.albumName,
                albumYear = row.albumYear,
                discNumber = track.discNumber,
                trackNumber = track.trackNumber,
                durationMs = track.durationMs,
                artworkPath = artwork.artworkFor(track.albumId, row.storedArtworkPath, row.localPath)
            )
        }
    }

    private fun albumSummaryOf(row: AlbumSummaryRow) = AlbumSummary(
        albumId = row.albumId,
        name = row.name,
        albumArtist = row.albumArtist,
        year = row.year,
        downloadedTrackCount = row.downloadedCount,
        artworkPath = chooseArtwork(row.storedArtworkPath, row.firstTrackPath, ::fileExists)
    )

    private fun selectedAlbumIds(): Set<String> =
        appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getStringSet(SELECTED_ALBUMS_KEY, emptySet()) ?: emptySet()

    private fun fileExists(path: String): Boolean = File(path).exists()
}
