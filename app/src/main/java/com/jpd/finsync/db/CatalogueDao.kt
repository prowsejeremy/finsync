package com.jpd.finsync.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CatalogueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAlbums(albums: List<CatalogueAlbum>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTracks(tracks: List<CatalogueTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertArtists(artists: List<CatalogueArtist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAlbumArtists(links: List<CatalogueAlbumArtist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTrackArtists(links: List<CatalogueTrackArtist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertGenres(genres: List<CatalogueGenre>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTrackGenres(links: List<CatalogueTrackGenre>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylists(playlists: List<CataloguePlaylist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylistItems(items: List<CataloguePlaylistItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBooks(books: List<CatalogueBook>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBookChapters(chapters: List<CatalogueBookChapter>)

    @Query("DELETE FROM catalogue_albums")
    abstract suspend fun deleteAllAlbums()

    @Query("DELETE FROM catalogue_tracks")
    abstract suspend fun deleteAllTracks()

    @Query("DELETE FROM catalogue_artists")
    abstract suspend fun deleteAllArtists()

    @Query("DELETE FROM catalogue_album_artists")
    abstract suspend fun deleteAllAlbumArtists()

    @Query("DELETE FROM catalogue_track_artists")
    abstract suspend fun deleteAllTrackArtists()

    @Query("DELETE FROM catalogue_genres")
    abstract suspend fun deleteAllGenres()

    @Query("DELETE FROM catalogue_track_genres")
    abstract suspend fun deleteAllTrackGenres()

    @Query("DELETE FROM catalogue_playlists")
    abstract suspend fun deleteAllPlaylists()

    @Query("DELETE FROM catalogue_playlist_items")
    abstract suspend fun deleteAllPlaylistItems()

    @Query("DELETE FROM catalogue_books")
    abstract suspend fun deleteAllBooks()

    @Query("DELETE FROM catalogue_book_chapters")
    abstract suspend fun deleteAllBookChapters()

    /** Swaps all eleven catalogue tables in one transaction, so readers never see half of it. */
    @Transaction
    open suspend fun replaceCatalogue(
        albums: List<CatalogueAlbum>,
        tracks: List<CatalogueTrack>,
        artists: List<CatalogueArtist>,
        albumArtists: List<CatalogueAlbumArtist>,
        trackArtists: List<CatalogueTrackArtist>,
        genres: List<CatalogueGenre>,
        trackGenres: List<CatalogueTrackGenre>,
        playlists: List<CataloguePlaylist>,
        playlistItems: List<CataloguePlaylistItem>,
        books: List<CatalogueBook>,
        bookChapters: List<CatalogueBookChapter>
    ) {
        clearCatalogue()
        insertAlbums(albums)
        insertTracks(tracks)
        insertArtists(artists)
        insertAlbumArtists(albumArtists)
        insertTrackArtists(trackArtists)
        insertGenres(genres)
        insertTrackGenres(trackGenres)
        insertPlaylists(playlists)
        insertPlaylistItems(playlistItems)
        insertBooks(books)
        insertBookChapters(bookChapters)
    }

    // book_progress isn't catalogue, so neither function touches it (spec "Book progress").
    @Transaction
    open suspend fun clearCatalogue() {
        deleteAllBookChapters()
        deleteAllBooks()
        deleteAllPlaylistItems()
        deleteAllPlaylists()
        deleteAllTrackGenres()
        deleteAllGenres()
        deleteAllTrackArtists()
        deleteAllAlbumArtists()
        deleteAllArtists()
        deleteAllTracks()
        deleteAllAlbums()
    }

    @Query("SELECT COUNT(*) FROM catalogue_tracks")
    abstract fun observeTrackCount(): Flow<Int>

    /** Every catalogue track's ID and album, for the Sync card's counts (3b refinements). */
    @Query("SELECT itemId, albumId FROM catalogue_tracks")
    abstract suspend fun trackAlbums(): List<TrackAlbumRow>

    @Query(
        """
        SELECT a.albumId AS albumId, a.name AS name, a.albumArtist AS albumArtist, a.year AS year,
            COUNT(t.itemId) AS downloadedCount, sa.artworkPath AS storedArtworkPath,
            MIN(s.localPath) AS firstTrackPath
        FROM catalogue_albums a
        INNER JOIN catalogue_tracks t ON t.albumId = a.albumId
        INNER JOIN synced_tracks s ON s.itemId = t.itemId
        LEFT JOIN synced_albums sa ON sa.albumId = a.albumId
        GROUP BY a.albumId
        ORDER BY a.name COLLATE NOCASE, a.albumId
        """
    )
    abstract fun observeAlbumSummaries(): Flow<List<AlbumSummaryRow>>

    @Query(
        """
        SELECT a.albumId AS albumId, a.name AS name, a.albumArtist AS albumArtist, a.year AS year,
            sa.artworkPath AS storedArtworkPath
        FROM catalogue_albums a
        LEFT JOIN synced_albums sa ON sa.albumId = a.albumId
        WHERE a.albumId = :albumId
        """
    )
    abstract fun observeAlbumHeader(albumId: String): Flow<AlbumHeaderRow?>

    @Query(
        """
        SELECT t.*, s.localPath AS localPath
        FROM catalogue_tracks t
        INNER JOIN synced_tracks s ON s.itemId = t.itemId
        WHERE t.albumId = :albumId
        ORDER BY COALESCE(t.discNumber, 1), COALESCE(t.trackNumber, 2147483647),
            t.name COLLATE NOCASE
        """
    )
    abstract fun observeDownloadedTracks(albumId: String): Flow<List<DownloadedTrackRow>>

    /** Album-artist credits on albums with a downloaded track, before the selection applies. */
    @Query(
        """
        SELECT aa.artistId AS artistId, ar.name AS name, aa.albumId AS albumId
        FROM catalogue_album_artists aa
        INNER JOIN catalogue_artists ar ON ar.artistId = aa.artistId
        WHERE EXISTS (
            SELECT 1 FROM catalogue_tracks t
            INNER JOIN synced_tracks s ON s.itemId = t.itemId
            WHERE t.albumId = aa.albumId
        )
        """
    )
    abstract fun observeAlbumArtistCredits(): Flow<List<ArtistAlbumRow>>

    /** Sync's artist photos: album artists of albums with a downloaded track. */
    @Query(
        """
        SELECT DISTINCT aa.artistId
        FROM catalogue_album_artists aa
        WHERE EXISTS (
            SELECT 1 FROM catalogue_tracks t
            INNER JOIN synced_tracks s ON s.itemId = t.itemId
            WHERE t.albumId = aa.albumId
        )
        """
    )
    abstract suspend fun downloadedAlbumArtistIds(): List<String>

    @Query("SELECT name FROM catalogue_artists WHERE artistId = :artistId")
    abstract fun observeArtistName(artistId: String): Flow<String?>

    /** Albums with a downloaded track where the artist is an album artist. */
    @Query(
        """
        SELECT a.albumId AS albumId, a.name AS name, a.albumArtist AS albumArtist, a.year AS year,
            COUNT(t.itemId) AS downloadedCount, sa.artworkPath AS storedArtworkPath,
            MIN(s.localPath) AS firstTrackPath
        FROM catalogue_albums a
        INNER JOIN catalogue_album_artists aa ON aa.albumId = a.albumId
        INNER JOIN catalogue_tracks t ON t.albumId = a.albumId
        INNER JOIN synced_tracks s ON s.itemId = t.itemId
        LEFT JOIN synced_albums sa ON sa.albumId = a.albumId
        WHERE aa.artistId = :artistId
        GROUP BY a.albumId
        """
    )
    abstract fun observeArtistAlbums(artistId: String): Flow<List<AlbumSummaryRow>>

    /** Downloaded tracks on the artist's albums, plus downloaded tracks anywhere that credit them. */
    @Query(
        """
        SELECT t.*, s.localPath AS localPath, a.name AS albumName, a.year AS albumYear,
            sa.artworkPath AS storedArtworkPath
        FROM catalogue_tracks t
        INNER JOIN synced_tracks s ON s.itemId = t.itemId
        LEFT JOIN catalogue_albums a ON a.albumId = t.albumId
        LEFT JOIN synced_albums sa ON sa.albumId = t.albumId
        WHERE t.albumId IN (SELECT albumId FROM catalogue_album_artists WHERE artistId = :artistId)
            OR t.itemId IN (SELECT itemId FROM catalogue_track_artists WHERE artistId = :artistId)
        """
    )
    abstract fun observeArtistSongs(artistId: String): Flow<List<SongTrackRow>>

    /** Genre tags on downloaded tracks, before the selection applies. */
    @Query(
        """
        SELECT g.genreId AS genreId, g.name AS name, tg.itemId AS itemId, t.albumId AS albumId
        FROM catalogue_track_genres tg
        INNER JOIN catalogue_genres g ON g.genreId = tg.genreId
        INNER JOIN catalogue_tracks t ON t.itemId = tg.itemId
        INNER JOIN synced_tracks s ON s.itemId = tg.itemId
        """
    )
    abstract fun observeGenreTags(): Flow<List<GenreTrackRow>>

    @Query("SELECT name FROM catalogue_genres WHERE genreId = :genreId")
    abstract fun observeGenreName(genreId: String): Flow<String?>

    /** Albums holding at least one downloaded track tagged with the genre. */
    @Query(
        """
        SELECT a.albumId AS albumId, a.name AS name, a.albumArtist AS albumArtist, a.year AS year,
            COUNT(t.itemId) AS downloadedCount, sa.artworkPath AS storedArtworkPath,
            MIN(s.localPath) AS firstTrackPath
        FROM catalogue_albums a
        INNER JOIN catalogue_tracks t ON t.albumId = a.albumId
        INNER JOIN synced_tracks s ON s.itemId = t.itemId
        LEFT JOIN synced_albums sa ON sa.albumId = a.albumId
        WHERE a.albumId IN (
            SELECT gt.albumId FROM catalogue_track_genres tg
            INNER JOIN catalogue_tracks gt ON gt.itemId = tg.itemId
            INNER JOIN synced_tracks gs ON gs.itemId = tg.itemId
            WHERE tg.genreId = :genreId
        )
        GROUP BY a.albumId
        """
    )
    abstract fun observeGenreAlbums(genreId: String): Flow<List<AlbumSummaryRow>>

    @Query(
        """
        SELECT t.*, s.localPath AS localPath, a.name AS albumName, a.year AS albumYear,
            sa.artworkPath AS storedArtworkPath
        FROM catalogue_tracks t
        INNER JOIN synced_tracks s ON s.itemId = t.itemId
        LEFT JOIN catalogue_albums a ON a.albumId = t.albumId
        LEFT JOIN synced_albums sa ON sa.albumId = t.albumId
        WHERE t.itemId IN (SELECT itemId FROM catalogue_track_genres WHERE genreId = :genreId)
        """
    )
    abstract fun observeGenreSongs(genreId: String): Flow<List<SongTrackRow>>

    /** Every downloaded track, A–Z by title ignoring case, before the selection applies. */
    @Query(
        """
        SELECT t.*, s.localPath AS localPath, a.name AS albumName, a.year AS albumYear,
            sa.artworkPath AS storedArtworkPath
        FROM catalogue_tracks t
        INNER JOIN synced_tracks s ON s.itemId = t.itemId
        LEFT JOIN catalogue_albums a ON a.albumId = t.albumId
        LEFT JOIN synced_albums sa ON sa.albumId = t.albumId
        ORDER BY t.name COLLATE NOCASE, t.itemId
        """
    )
    abstract fun observeSongs(): Flow<List<SongTrackRow>>

    /** Each downloaded track's album (null for none), for Home's song count. */
    @Query(
        """
        SELECT t.albumId FROM catalogue_tracks t
        INNER JOIN synced_tracks s ON s.itemId = t.itemId
        """
    )
    abstract fun observeDownloadedTrackAlbumIds(): Flow<List<String?>>

    /** Downloaded tracks among [itemIds], in no set order. Callers keep each list under 999. */
    @Query(
        """
        SELECT t.*, s.localPath AS localPath, a.name AS albumName,
            (
                SELECT aa.artistId FROM catalogue_album_artists aa
                WHERE aa.albumId = t.albumId
                ORDER BY aa.position
                LIMIT 1
            ) AS albumArtistId,
            sa.artworkPath AS storedArtworkPath
        FROM catalogue_tracks t
        INNER JOIN synced_tracks s ON s.itemId = t.itemId
        LEFT JOIN catalogue_albums a ON a.albumId = t.albumId
        LEFT JOIN synced_albums sa ON sa.albumId = t.albumId
        WHERE t.itemId IN (:itemIds)
        """
    )
    abstract suspend fun playableTracks(itemIds: List<String>): List<PlayableTrackRow>

    @Query("SELECT * FROM catalogue_playlists")
    abstract fun observePlaylists(): Flow<List<CataloguePlaylist>>

    /** Every playlist's downloaded entries, before the selection applies. */
    @Query(
        """
        SELECT pi.playlistId AS playlistId, pi.position AS position, t.durationMs AS durationMs,
            t.albumId AS albumId, s.localPath AS localPath, sa.artworkPath AS storedArtworkPath
        FROM catalogue_playlist_items pi
        INNER JOIN catalogue_tracks t ON t.itemId = pi.itemId
        INNER JOIN synced_tracks s ON s.itemId = pi.itemId
        LEFT JOIN synced_albums sa ON sa.albumId = t.albumId
        ORDER BY pi.playlistId, pi.position
        """
    )
    abstract fun observePlaylistEntries(): Flow<List<PlaylistEntryRow>>

    @Query("SELECT name FROM catalogue_playlists WHERE playlistId = :playlistId")
    abstract fun observePlaylistName(playlistId: String): Flow<String?>

    /** A playlist's downloaded entries in server order; a repeated song gives repeated rows. */
    @Query(
        """
        SELECT t.*, s.localPath AS localPath, a.name AS albumName, a.year AS albumYear,
            sa.artworkPath AS storedArtworkPath
        FROM catalogue_playlist_items pi
        INNER JOIN catalogue_tracks t ON t.itemId = pi.itemId
        INNER JOIN synced_tracks s ON s.itemId = pi.itemId
        LEFT JOIN catalogue_albums a ON a.albumId = t.albumId
        LEFT JOIN synced_albums sa ON sa.albumId = t.albumId
        WHERE pi.playlistId = :playlistId
        ORDER BY pi.position
        """
    )
    abstract fun observePlaylistSongs(playlistId: String): Flow<List<SongTrackRow>>

    @Query(
        """
        SELECT p.playlistId AS playlistId, p.name AS name, COUNT(pi.itemId) AS entryCount
        FROM catalogue_playlists p
        LEFT JOIN catalogue_playlist_items pi ON pi.playlistId = p.playlistId
        GROUP BY p.playlistId
        ORDER BY p.name COLLATE NOCASE, p.playlistId
        """
    )
    abstract fun observePlaylistChoices(): Flow<List<PlaylistChoiceRow>>

    /** Albums holding a downloaded playlist entry, for the visibility rule (spec "Visibility"). */
    @Query(
        """
        SELECT DISTINCT pi.playlistId AS playlistId, t.albumId AS albumId
        FROM catalogue_playlist_items pi
        INNER JOIN catalogue_tracks t ON t.itemId = pi.itemId
        INNER JOIN synced_tracks s ON s.itemId = pi.itemId
        WHERE t.albumId IS NOT NULL
        """
    )
    abstract suspend fun playlistAlbums(): List<PlaylistAlbumRow>

    // The playlist and book rows as they are, so a part whose fetch failed can keep them
    // (spec "Order, cleanup and failures").

    @Query("SELECT * FROM catalogue_playlists")
    abstract suspend fun allPlaylists(): List<CataloguePlaylist>

    @Query("SELECT * FROM catalogue_playlist_items ORDER BY playlistId, position")
    abstract suspend fun allPlaylistItems(): List<CataloguePlaylistItem>

    @Query("SELECT * FROM catalogue_books")
    abstract suspend fun allBooks(): List<CatalogueBook>

    @Query("SELECT * FROM catalogue_book_chapters ORDER BY bookId, position")
    abstract suspend fun allBookChapters(): List<CatalogueBookChapter>

    /** Books with a downloaded file, before the selection applies. */
    @Query(
        """
        SELECT b.*, s.localPath AS localPath
        FROM catalogue_books b
        INNER JOIN synced_tracks s ON s.itemId = b.bookId
        """
    )
    abstract fun observeDownloadedBooks(): Flow<List<DownloadedBookRow>>

    @Query(
        """
        SELECT b.*, s.localPath AS localPath
        FROM catalogue_books b
        INNER JOIN synced_tracks s ON s.itemId = b.bookId
        WHERE b.bookId = :bookId
        """
    )
    abstract fun observeDownloadedBook(bookId: String): Flow<DownloadedBookRow?>

    /** Downloaded books among [bookIds], in no set order. Callers keep each list under 999. */
    @Query(
        """
        SELECT b.*, s.localPath AS localPath
        FROM catalogue_books b
        INNER JOIN synced_tracks s ON s.itemId = b.bookId
        WHERE b.bookId IN (:bookIds)
        """
    )
    abstract suspend fun downloadedBooks(bookIds: List<String>): List<DownloadedBookRow>

    @Query("SELECT * FROM catalogue_book_chapters ORDER BY bookId, position")
    abstract fun observeAllChapters(): Flow<List<CatalogueBookChapter>>

    @Query("SELECT * FROM catalogue_book_chapters WHERE bookId = :bookId ORDER BY position")
    abstract fun observeChapters(bookId: String): Flow<List<CatalogueBookChapter>>

    /** Chapters of [bookIds], each book's in order. Callers keep each list under 999. */
    @Query(
        """
        SELECT * FROM catalogue_book_chapters
        WHERE bookId IN (:bookIds)
        ORDER BY bookId, position
        """
    )
    abstract suspend fun chaptersOf(bookIds: List<String>): List<CatalogueBookChapter>

    @Query("SELECT * FROM catalogue_books ORDER BY name COLLATE NOCASE, bookId")
    abstract fun observeBookChoices(): Flow<List<CatalogueBook>>

    // book_progress is outside the catalogue: replaceCatalogue and clearCatalogue never touch it.

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertBookProgress(progress: BookProgress)

    @Query("SELECT * FROM book_progress")
    abstract fun observeBookProgress(): Flow<List<BookProgress>>

    @Query("SELECT * FROM book_progress WHERE bookId = :bookId")
    abstract fun observeProgress(bookId: String): Flow<BookProgress?>

    @Query("SELECT * FROM book_progress WHERE bookId = :bookId")
    abstract suspend fun bookProgress(bookId: String): BookProgress?

    @Query("DELETE FROM book_progress")
    abstract suspend fun deleteAllBookProgress()
}
