package com.jpd.hz.library.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Everything one scan writes, table by table. */
data class LibraryContents(
    val files: List<LibraryFile>,
    val tracks: List<LibraryTrack>,
    val albums: List<LibraryAlbum>,
    val artists: List<LibraryArtist>,
    val albumArtists: List<LibraryAlbumArtist>,
    val trackArtists: List<LibraryTrackArtist>,
    val genres: List<LibraryGenre>,
    val trackGenres: List<LibraryTrackGenre>,
    val playlists: List<LibraryPlaylist>,
    val playlistItems: List<LibraryPlaylistItem>,
    val books: List<LibraryBook>,
    val chapters: List<LibraryBookChapter>
)

/** What the scanner reads and writes. Screens read through LibraryDao instead. */
@Dao
abstract class LibraryScanDao {

    @Query("SELECT * FROM library_files")
    abstract suspend fun files(): List<LibraryFile>

    @Query("SELECT * FROM library_tracks")
    abstract suspend fun tracks(): List<LibraryTrack>

    @Query("SELECT * FROM library_books")
    abstract suspend fun books(): List<LibraryBook>

    @Query("SELECT * FROM library_book_chapters ORDER BY bookId, position")
    abstract suspend fun chapters(): List<LibraryBookChapter>

    /**
     * Replaces the scan's tables in one transaction, so readers never see half a scan. Book
     * progress stays, except for books that have gone (A2).
     */
    @Transaction
    open suspend fun replaceLibrary(contents: LibraryContents) {
        clearLibrary()
        insertFiles(contents.files)
        insertTracks(contents.tracks)
        insertAlbums(contents.albums)
        insertArtists(contents.artists)
        insertAlbumArtists(contents.albumArtists)
        insertTrackArtists(contents.trackArtists)
        insertGenres(contents.genres)
        insertTrackGenres(contents.trackGenres)
        insertPlaylists(contents.playlists)
        insertPlaylistItems(contents.playlistItems)
        insertBooks(contents.books)
        insertChapters(contents.chapters)
        deleteProgressOfGoneBooks()
    }

    // The scan's tables only: book_progress isn't the scan's.
    @Transaction
    open suspend fun clearLibrary() {
        deleteFiles()
        deleteTracks()
        deleteAlbums()
        deleteArtists()
        deleteAlbumArtists()
        deleteTrackArtists()
        deleteGenres()
        deleteTrackGenres()
        deletePlaylists()
        deletePlaylistItems()
        deleteBooks()
        deleteChapters()
    }

    @Insert
    protected abstract suspend fun insertFiles(rows: List<LibraryFile>)

    @Insert
    protected abstract suspend fun insertTracks(rows: List<LibraryTrack>)

    @Insert
    protected abstract suspend fun insertAlbums(rows: List<LibraryAlbum>)

    @Insert
    protected abstract suspend fun insertArtists(rows: List<LibraryArtist>)

    @Insert
    protected abstract suspend fun insertAlbumArtists(rows: List<LibraryAlbumArtist>)

    @Insert
    protected abstract suspend fun insertTrackArtists(rows: List<LibraryTrackArtist>)

    @Insert
    protected abstract suspend fun insertGenres(rows: List<LibraryGenre>)

    @Insert
    protected abstract suspend fun insertTrackGenres(rows: List<LibraryTrackGenre>)

    @Insert
    protected abstract suspend fun insertPlaylists(rows: List<LibraryPlaylist>)

    @Insert
    protected abstract suspend fun insertPlaylistItems(rows: List<LibraryPlaylistItem>)

    @Insert
    protected abstract suspend fun insertBooks(rows: List<LibraryBook>)

    @Insert
    protected abstract suspend fun insertChapters(rows: List<LibraryBookChapter>)

    @Query("DELETE FROM library_files")
    protected abstract suspend fun deleteFiles()

    @Query("DELETE FROM library_tracks")
    protected abstract suspend fun deleteTracks()

    @Query("DELETE FROM library_albums")
    protected abstract suspend fun deleteAlbums()

    @Query("DELETE FROM library_artists")
    protected abstract suspend fun deleteArtists()

    @Query("DELETE FROM library_album_artists")
    protected abstract suspend fun deleteAlbumArtists()

    @Query("DELETE FROM library_track_artists")
    protected abstract suspend fun deleteTrackArtists()

    @Query("DELETE FROM library_genres")
    protected abstract suspend fun deleteGenres()

    @Query("DELETE FROM library_track_genres")
    protected abstract suspend fun deleteTrackGenres()

    @Query("DELETE FROM library_playlists")
    protected abstract suspend fun deletePlaylists()

    @Query("DELETE FROM library_playlist_items")
    protected abstract suspend fun deletePlaylistItems()

    @Query("DELETE FROM library_books")
    protected abstract suspend fun deleteBooks()

    @Query("DELETE FROM library_book_chapters")
    protected abstract suspend fun deleteChapters()

    @Query("DELETE FROM book_progress WHERE bookId NOT IN (SELECT bookId FROM library_books)")
    protected abstract suspend fun deleteProgressOfGoneBooks()
}
