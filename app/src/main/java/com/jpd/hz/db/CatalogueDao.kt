package com.jpd.hz.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The Jellyfin adapter's copy of the server's catalogue (T3): what its sync plans and counts from,
 * and what its choice screens list. The player reads the Library folder instead.
 */
@Dao
abstract class CatalogueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTracks(tracks: List<CatalogueTrack>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylists(playlists: List<CataloguePlaylist>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPlaylistItems(items: List<CataloguePlaylistItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertBooks(books: List<CatalogueBook>)

    @Query("DELETE FROM catalogue_tracks")
    abstract suspend fun deleteAllTracks()

    @Query("DELETE FROM catalogue_playlists")
    abstract suspend fun deleteAllPlaylists()

    @Query("DELETE FROM catalogue_playlist_items")
    abstract suspend fun deleteAllPlaylistItems()

    @Query("DELETE FROM catalogue_books")
    abstract suspend fun deleteAllBooks()

    /** Swaps the four catalogue tables in one transaction, so readers never see half of it. */
    @Transaction
    open suspend fun replaceCatalogue(
        tracks: List<CatalogueTrack>,
        playlists: List<CataloguePlaylist>,
        playlistItems: List<CataloguePlaylistItem>,
        books: List<CatalogueBook>
    ) {
        clearCatalogue()
        insertTracks(tracks)
        insertPlaylists(playlists)
        insertPlaylistItems(playlistItems)
        insertBooks(books)
    }

    @Transaction
    open suspend fun clearCatalogue() {
        deleteAllBooks()
        deleteAllPlaylistItems()
        deleteAllPlaylists()
        deleteAllTracks()
    }

    @Query("SELECT COUNT(*) FROM catalogue_tracks")
    abstract suspend fun trackCount(): Int

    /** Every catalogue track's ID and album, for the Sync card's counts (3b refinements). */
    @Query("SELECT itemId, albumId FROM catalogue_tracks")
    abstract suspend fun trackAlbums(): List<TrackAlbumRow>

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

    @Query("SELECT * FROM catalogue_books ORDER BY name COLLATE NOCASE, bookId")
    abstract fun observeBookChoices(): Flow<List<CatalogueBook>>

    // The playlist and book rows as they are, so a part whose fetch failed can keep them
    // (spec "Order, cleanup and failures").

    @Query("SELECT * FROM catalogue_playlists")
    abstract suspend fun allPlaylists(): List<CataloguePlaylist>

    @Query("SELECT * FROM catalogue_playlist_items ORDER BY playlistId, position")
    abstract suspend fun allPlaylistItems(): List<CataloguePlaylistItem>

    @Query("SELECT * FROM catalogue_books")
    abstract suspend fun allBooks(): List<CatalogueBook>
}
