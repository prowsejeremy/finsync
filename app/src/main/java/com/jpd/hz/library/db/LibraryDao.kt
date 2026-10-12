package com.jpd.hz.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// The album list's columns, shared by every query that lists albums.
private const val ALBUM_COLUMNS =
    "a.albumId AS albumId, a.name AS name, a.albumArtist AS albumArtist, a.year AS year, " +
        "a.artworkPath AS artworkPath, a.embeddedArt AS embeddedArt, " +
        "COUNT(t.trackId) AS trackCount"

// A song row's columns: the track, plus its album's name, year and artwork.
private const val SONG_COLUMNS =
    "t.*, a.name AS albumName, a.year AS albumYear, a.artworkPath AS artworkPath, " +
        "a.embeddedArt AS embeddedArt"

/**
 * What the repositories read (overview rule 2: screens never call it). Queries are Room Flows, so
 * screens update when a scan writes.
 */
@Dao
interface LibraryDao {

    /** Tracks and books together: zero means the library is empty. */
    @Query("SELECT (SELECT COUNT(*) FROM library_tracks) + (SELECT COUNT(*) FROM library_books)")
    fun observeItemCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM library_tracks")
    fun observeTrackCount(): Flow<Int>

    @Query(
        """
        SELECT $ALBUM_COLUMNS
        FROM library_albums a
        INNER JOIN library_tracks t ON t.albumId = a.albumId
        GROUP BY a.albumId
        ORDER BY a.name COLLATE NOCASE, a.albumId
        """
    )
    fun observeAlbums(): Flow<List<AlbumRow>>

    @Query("SELECT * FROM library_albums WHERE albumId = :albumId")
    fun observeAlbum(albumId: String): Flow<LibraryAlbum?>

    /** In disc, number and title order; a track without a disc counts as disc 1. */
    @Query(
        """
        SELECT * FROM library_tracks
        WHERE albumId = :albumId
        ORDER BY COALESCE(discNumber, 1), COALESCE(trackNumber, 2147483647),
            title COLLATE NOCASE, trackId
        """
    )
    fun observeAlbumTracks(albumId: String): Flow<List<LibraryTrack>>

    @Query(
        """
        SELECT aa.artistId AS artistId, ar.name AS name, ar.photoPath AS photoPath,
            aa.albumId AS albumId
        FROM library_album_artists aa
        INNER JOIN library_artists ar ON ar.artistId = aa.artistId
        """
    )
    fun observeAlbumArtistCredits(): Flow<List<ArtistCreditRow>>

    @Query("SELECT * FROM library_artists WHERE artistId = :artistId")
    fun observeArtist(artistId: String): Flow<LibraryArtist?>

    /** Albums where the artist is an album artist. */
    @Query(
        """
        SELECT $ALBUM_COLUMNS
        FROM library_albums a
        INNER JOIN library_album_artists aa ON aa.albumId = a.albumId
        INNER JOIN library_tracks t ON t.albumId = a.albumId
        WHERE aa.artistId = :artistId
        GROUP BY a.albumId
        """
    )
    fun observeArtistAlbums(artistId: String): Flow<List<AlbumRow>>

    /** Tracks on the artist's albums, plus tracks anywhere that credit them. */
    @Query(
        """
        SELECT $SONG_COLUMNS
        FROM library_tracks t
        LEFT JOIN library_albums a ON a.albumId = t.albumId
        WHERE t.albumId IN (SELECT albumId FROM library_album_artists WHERE artistId = :artistId)
            OR t.trackId IN (SELECT trackId FROM library_track_artists WHERE artistId = :artistId)
        """
    )
    fun observeArtistSongs(artistId: String): Flow<List<SongTrackRow>>

    @Query(
        """
        SELECT g.genreId AS genreId, g.name AS name, tg.trackId AS trackId, t.albumId AS albumId
        FROM library_track_genres tg
        INNER JOIN library_genres g ON g.genreId = tg.genreId
        INNER JOIN library_tracks t ON t.trackId = tg.trackId
        """
    )
    fun observeGenreTags(): Flow<List<GenreTrackRow>>

    @Query("SELECT name FROM library_genres WHERE genreId = :genreId")
    fun observeGenreName(genreId: String): Flow<String?>

    /** Albums holding at least one track in the genre. */
    @Query(
        """
        SELECT $ALBUM_COLUMNS
        FROM library_albums a
        INNER JOIN library_tracks t ON t.albumId = a.albumId
        WHERE a.albumId IN (
            SELECT gt.albumId FROM library_track_genres tg
            INNER JOIN library_tracks gt ON gt.trackId = tg.trackId
            WHERE tg.genreId = :genreId
        )
        GROUP BY a.albumId
        """
    )
    fun observeGenreAlbums(genreId: String): Flow<List<AlbumRow>>

    @Query(
        """
        SELECT $SONG_COLUMNS
        FROM library_tracks t
        LEFT JOIN library_albums a ON a.albumId = t.albumId
        WHERE t.trackId IN (SELECT trackId FROM library_track_genres WHERE genreId = :genreId)
        """
    )
    fun observeGenreSongs(genreId: String): Flow<List<SongTrackRow>>

    /** Every track, A–Z by title ignoring case. */
    @Query(
        """
        SELECT $SONG_COLUMNS
        FROM library_tracks t
        LEFT JOIN library_albums a ON a.albumId = t.albumId
        ORDER BY t.title COLLATE NOCASE, t.trackId
        """
    )
    fun observeSongs(): Flow<List<SongTrackRow>>

    /** Tracks among [trackIds], in no set order. Callers keep each list under 999. */
    @Query(
        """
        SELECT t.*, f.path AS path, a.name AS albumName, a.albumArtist AS albumArtist,
            (
                SELECT aa.artistId FROM library_album_artists aa
                WHERE aa.albumId = t.albumId
                ORDER BY aa.position
                LIMIT 1
            ) AS albumArtistId,
            a.artworkPath AS artworkPath, a.embeddedArt AS embeddedArt
        FROM library_tracks t
        INNER JOIN library_files f ON f.fileId = t.trackId
        LEFT JOIN library_albums a ON a.albumId = t.albumId
        WHERE t.trackId IN (:trackIds)
        """
    )
    suspend fun playableTracks(trackIds: List<String>): List<PlayableTrackRow>

    @Query("SELECT * FROM library_playlists")
    fun observePlaylists(): Flow<List<LibraryPlaylist>>

    @Query(
        """
        SELECT pi.playlistId AS playlistId, pi.position AS position, t.durationMs AS durationMs,
            a.artworkPath AS artworkPath, a.embeddedArt AS embeddedArt
        FROM library_playlist_items pi
        INNER JOIN library_tracks t ON t.trackId = pi.trackId
        LEFT JOIN library_albums a ON a.albumId = t.albumId
        ORDER BY pi.playlistId, pi.position
        """
    )
    fun observePlaylistEntries(): Flow<List<PlaylistEntryRow>>

    @Query("SELECT * FROM library_playlists WHERE playlistId = :playlistId")
    fun observePlaylist(playlistId: String): Flow<LibraryPlaylist?>

    /** A playlist's songs in file order; a repeated song gives repeated rows. */
    @Query(
        """
        SELECT $SONG_COLUMNS
        FROM library_playlist_items pi
        INNER JOIN library_tracks t ON t.trackId = pi.trackId
        LEFT JOIN library_albums a ON a.albumId = t.albumId
        WHERE pi.playlistId = :playlistId
        ORDER BY pi.position
        """
    )
    fun observePlaylistSongs(playlistId: String): Flow<List<SongTrackRow>>

    @Query("SELECT * FROM library_books")
    fun observeBooks(): Flow<List<LibraryBook>>

    @Query("SELECT COUNT(*) FROM library_books")
    fun observeBookCount(): Flow<Int>

    @Query("SELECT * FROM library_books WHERE bookId = :bookId")
    fun observeBook(bookId: String): Flow<LibraryBook?>

    @Query("SELECT * FROM library_book_chapters ORDER BY bookId, position")
    fun observeAllChapters(): Flow<List<LibraryBookChapter>>

    @Query("SELECT * FROM library_book_chapters WHERE bookId = :bookId ORDER BY position")
    fun observeChapters(bookId: String): Flow<List<LibraryBookChapter>>

    /** Books among [bookIds] with their paths, in no set order. Callers keep lists under 999. */
    @Query(
        """
        SELECT b.*, f.path AS path FROM library_books b
        INNER JOIN library_files f ON f.fileId = b.bookId
        WHERE b.bookId IN (:bookIds)
        """
    )
    suspend fun books(bookIds: List<String>): List<BookFileRow>

    /** Chapters of [bookIds], each book's in order. Callers keep each list under 999. */
    @Query(
        """
        SELECT * FROM library_book_chapters
        WHERE bookId IN (:bookIds)
        ORDER BY bookId, position
        """
    )
    suspend fun chaptersOf(bookIds: List<String>): List<LibraryBookChapter>

    // Book progress (A3): no scan writes it, and the scan's write deletes it only for books that
    // have gone.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookProgress(progress: BookProgress)

    @Query("SELECT * FROM book_progress")
    fun observeBookProgress(): Flow<List<BookProgress>>

    @Query("SELECT * FROM book_progress WHERE bookId = :bookId")
    fun observeProgress(bookId: String): Flow<BookProgress?>

    @Query("SELECT * FROM book_progress WHERE bookId = :bookId")
    suspend fun bookProgress(bookId: String): BookProgress?
}
