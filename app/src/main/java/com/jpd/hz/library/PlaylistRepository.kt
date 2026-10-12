package com.jpd.hz.library

import android.content.Context
import com.jpd.hz.library.db.LibraryDao
import com.jpd.hz.library.db.LibraryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Where screens read playlists from (overview rule 2): Playlists and the playlist page. Every
 * playlist file in the Library folder shows once it has a song in the library.
 */
class PlaylistRepository internal constructor(
    private val dao: LibraryDao,
    private val files: LibraryFiles
) {

    constructor(context: Context) : this(
        LibraryDatabase.getInstance(context.applicationContext).libraryDao(),
        LibraryFiles.of(context)
    )

    /** Playlists with at least one song, A–Z. */
    fun playlists(): Flow<List<PlaylistSummary>> =
        combine(dao.observePlaylists(), dao.observePlaylistEntries()) { playlists, entries ->
            playlistSummaries(
                playlists.map { it.copy(coverPath = files.image(it.coverPath)) },
                entries.map { it.copy(artworkPath = files.art(it.artworkPath, it.embeddedArt)) }
            )
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun playlistCount(): Flow<Int> = playlists().map { it.size }.distinctUntilChanged()

    /**
     * A playlist's songs in file order, a song repeated where the playlist repeats it. Null once
     * the file is gone or none of its songs is in the library.
     */
    fun playlist(playlistId: String): Flow<PlaylistDetail?> =
        combine(
            dao.observePlaylist(playlistId),
            dao.observePlaylistSongs(playlistId)
        ) { playlist, rows ->
            if (playlist == null || rows.isEmpty()) {
                null
            } else {
                val songs = rows.map { songRowOf(it, files) }
                PlaylistDetail(
                    playlistId = playlistId,
                    name = playlist.name,
                    coverPath = files.image(playlist.coverPath) ?: songs.first().artworkPath,
                    songs = songs
                )
            }
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)
}
