package com.jpd.hz.library

import android.content.Context
import com.jpd.hz.library.db.AlbumRow
import com.jpd.hz.library.db.LibraryDao
import com.jpd.hz.library.db.LibraryDatabase
import com.jpd.hz.library.db.SongTrackRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val ARTIST_SEPARATOR = ", "
// SQLite before 3.32 (Android before 11) allows 999 bound parameters in one statement.
private const val MAX_IDS_PER_QUERY = 900

/**
 * The one place screens and the playback service read music from (overview rule 2). Queries are
 * Room Flows over the scanner's library, so screens update when a scan writes. Everything in the
 * Library folder shows: the adapters' selections only drive their syncs (spec "Repositories").
 */
class LibraryRepository internal constructor(
    private val dao: LibraryDao,
    private val files: LibraryFiles
) {

    constructor(context: Context) : this(
        LibraryDatabase.getInstance(context.applicationContext).libraryDao(),
        LibraryFiles.of(context)
    )

    /** Every album, by name. */
    fun albums(): Flow<List<AlbumSummary>> =
        dao.observeAlbums()
            .map { rows -> rows.map(::albumSummaryOf) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun albumCount(): Flow<Int> = albums().map { it.size }.distinctUntilChanged()

    /** Artists who are album artist of at least one album, A–Z ignoring case. */
    fun albumArtists(): Flow<List<ArtistSummary>> =
        dao.observeAlbumArtistCredits()
            .conflate()
            .map { credits ->
                albumArtistSummaries(credits.map { it.copy(photoPath = files.image(it.photoPath)) })
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun albumArtistCount(): Flow<Int> = albumArtists().map { it.size }.distinctUntilChanged()

    /** Genres with at least one track, A–Z ignoring case. */
    fun genres(): Flow<List<GenreSummary>> =
        dao.observeGenreTags()
            .conflate()
            .map(::genreSummaries)
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun genreCount(): Flow<Int> = genres().map { it.size }.distinctUntilChanged()

    /** Every track, A–Z by title ignoring case. */
    fun songs(): Flow<List<SongRow>> =
        dao.observeSongs()
            .map { rows -> rows.map { songRowOf(it, files) } }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun songCount(): Flow<Int> = dao.observeTrackCount().distinctUntilChanged()

    /** No tracks and no books: Home shows the scan's progress or the empty state. */
    fun isLibraryEmpty(): Flow<Boolean> =
        dao.observeItemCount().map { it == 0 }.distinctUntilChanged()

    /** The album and its tracks, or null once it has none. */
    fun album(albumId: String): Flow<AlbumDetail?> =
        combine(dao.observeAlbum(albumId), dao.observeAlbumTracks(albumId)) { album, tracks ->
            if (album == null || tracks.isEmpty()) {
                null
            } else {
                AlbumDetail(
                    albumId = album.albumId,
                    name = album.name,
                    albumArtist = album.albumArtist,
                    year = album.year,
                    artworkPath = files.art(album.artworkPath, album.embeddedArt),
                    tracks = tracks
                )
            }
        }.flowOn(Dispatchers.IO)

    /**
     * The artist's page: albums where they're an album artist, and All songs, which adds tracks
     * elsewhere that credit them. Null once they have no songs.
     */
    fun artist(artistId: String): Flow<GroupDetail?> =
        combine(
            dao.observeArtist(artistId),
            dao.observeArtistAlbums(artistId),
            dao.observeArtistSongs(artistId)
        ) { artist, albumRows, songRows ->
            groupDetailOf(
                artistId,
                artist?.name,
                files.image(artist?.photoPath),
                albumRows.map(::albumSummaryOf),
                songRows.map { songRowOf(it, files) }
            )
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** The genre's page: albums with a track in the genre, and those tracks. */
    fun genre(genreId: String): Flow<GroupDetail?> =
        combine(
            dao.observeGenreName(genreId),
            dao.observeGenreAlbums(genreId),
            dao.observeGenreSongs(genreId)
        ) { name, albumRows, songRows ->
            groupDetailOf(
                genreId,
                name,
                null,
                albumRows.map(::albumSummaryOf),
                songRows.map { songRowOf(it, files) }
            )
        }
            .conflate()
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /**
     * What the resolver needs, one per ID in the order asked, null where the library has no such
     * track. A long queue takes a few chunked queries, not one per track. Files are found in
     * today's Library folder (A1), so a moved Library folder is followed at once.
     */
    suspend fun playableTracks(trackIds: List<String>): List<PlayableSource?> =
        withContext(Dispatchers.IO) {
            val rows = trackIds.distinct()
                .chunked(MAX_IDS_PER_QUERY)
                .flatMap { chunk -> dao.playableTracks(chunk) }
                .associateBy { it.track.trackId }
            trackIds.map { id ->
                rows[id]?.let { row ->
                    PlayableSource(
                        track = row.track,
                        localPath = files.file(row.path).path,
                        albumName = row.albumName,
                        albumArtist = row.albumArtist,
                        albumArtistId = row.albumArtistId,
                        artworkPath = files.art(row.artworkPath, row.embeddedArt)
                    )
                }
            }
        }

    private fun albumSummaryOf(row: AlbumRow) = AlbumSummary(
        albumId = row.albumId,
        name = row.name,
        albumArtist = row.albumArtist,
        year = row.year,
        downloadedTrackCount = row.trackCount,
        artworkPath = files.art(row.artworkPath, row.embeddedArt)
    )
}

/** A track as a Songs-style row: Songs, All songs and a playlist's page share it. */
internal fun songRowOf(row: SongTrackRow, files: LibraryFiles): SongRow {
    val track = row.track
    return SongRow(
        itemId = track.trackId,
        title = track.title,
        artists = (track.artistNames.ifEmpty { track.albumArtistNames })
            .takeIf { it.isNotEmpty() }
            ?.joinToString(ARTIST_SEPARATOR),
        albumId = track.albumId,
        albumName = row.albumName,
        albumYear = row.albumYear,
        discNumber = track.discNumber,
        trackNumber = track.trackNumber,
        durationMs = track.durationMs,
        artworkPath = files.art(row.artworkPath, row.embeddedArt)
    )
}
