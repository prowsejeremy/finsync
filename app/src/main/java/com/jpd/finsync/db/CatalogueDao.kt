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

    @Query("DELETE FROM catalogue_albums")
    abstract suspend fun deleteAllAlbums()

    @Query("DELETE FROM catalogue_tracks")
    abstract suspend fun deleteAllTracks()

    /** Swaps the whole catalogue in one transaction, so readers never see half of it. */
    @Transaction
    open suspend fun replaceCatalogue(albums: List<CatalogueAlbum>, tracks: List<CatalogueTrack>) {
        deleteAllTracks()
        deleteAllAlbums()
        insertAlbums(albums)
        insertTracks(tracks)
    }

    @Transaction
    open suspend fun clearCatalogue() {
        deleteAllTracks()
        deleteAllAlbums()
    }

    @Query("SELECT COUNT(*) FROM catalogue_tracks")
    abstract fun observeTrackCount(): Flow<Int>

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

    @Query(
        """
        SELECT t.*, s.localPath AS localPath, a.name AS albumName,
            sa.artworkPath AS storedArtworkPath
        FROM catalogue_tracks t
        INNER JOIN synced_tracks s ON s.itemId = t.itemId
        LEFT JOIN catalogue_albums a ON a.albumId = t.albumId
        LEFT JOIN synced_albums sa ON sa.albumId = t.albumId
        WHERE t.itemId = :itemId
        LIMIT 1
        """
    )
    abstract suspend fun playableTrack(itemId: String): PlayableTrackRow?
}
