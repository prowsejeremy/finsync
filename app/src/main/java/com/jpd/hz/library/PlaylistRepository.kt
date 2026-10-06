package com.jpd.hz.library

import android.content.Context
import com.jpd.hz.db.CatalogueDao
import com.jpd.hz.db.SyncDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where screens read playlists from (3b; overview rule 2): Playlists, the playlist page,
 * Playlists to Sync, the playlist selection and the covers. The catalogue write stays in
 * [LibraryRepository], so every catalogue table swaps in one transaction.
 */
class PlaylistRepository internal constructor(
    context: Context,
    private val catalogueDao: CatalogueDao
) {

    constructor(context: Context) : this(
        context,
        SyncDatabase.getInstance(context.applicationContext).catalogueDao()
    )

    private val appContext = context.applicationContext
    private val selections = SyncSelections(appContext)

    /** Selected playlists with at least one downloaded song, A–Z (spec "Playlists"). */
    fun playlists(): Flow<List<PlaylistSummary>> =
        combine(
            catalogueDao.observePlaylists(),
            catalogueDao.observePlaylistEntries()
        ) { playlists, entries ->
            val artwork = AlbumArtworkCache(::fileExists)
            playlistSummaries(playlists, entries, selections.playlistIds()) { playlistId, first ->
                PlaylistCovers.pathIfExists(appContext, playlistId)
                    ?: artwork.artworkFor(first.albumId, first.storedArtworkPath, first.localPath)
            }
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    // Home's count skips the cover file checks.
    fun playlistCount(): Flow<Int> =
        combine(
            catalogueDao.observePlaylists(),
            catalogueDao.observePlaylistEntries()
        ) { playlists, entries ->
            playlistSummaries(playlists, entries, selections.playlistIds()) { _, _ -> null }.size
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /**
     * A playlist's downloaded songs in server order, a song repeated where the playlist repeats
     * it. Null once it's gone from the server or none of it is downloaded.
     */
    fun playlist(playlistId: String): Flow<PlaylistDetail?> =
        combine(
            catalogueDao.observePlaylistName(playlistId),
            catalogueDao.observePlaylistSongs(playlistId)
        ) { name, rows ->
            if (name == null || rows.isEmpty()) {
                null
            } else {
                val artwork = AlbumArtworkCache(::fileExists)
                val songs = rows.map { songRowOf(it, artwork) }
                PlaylistDetail(
                    playlistId = playlistId,
                    name = name,
                    coverPath = PlaylistCovers.pathIfExists(appContext, playlistId)
                        ?: songs.first().artworkPath,
                    songs = songs
                )
            }
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** Every audio playlist on the server, A–Z, for Playlists to Sync. */
    fun playlistChoices(): Flow<List<PlaylistChoice>> =
        catalogueDao.observePlaylistChoices()
            .map { rows -> rows.map { PlaylistChoice(it.playlistId, it.name, it.entryCount) } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** The playlists chosen in Playlists to Sync; empty means none (spec). */
    fun selectedIds(): Set<String> = selections.playlistIds()

    fun setSelectedIds(ids: Set<String>) = selections.setPlaylistIds(ids)

    /** Logout: another server's playlist covers must never show (spec "Logout"). */
    suspend fun deletePlaylistCovers() = withContext(Dispatchers.IO) {
        PlaylistCovers.deleteAll(appContext)
    }

    private fun fileExists(path: String): Boolean = File(path).exists()
}
