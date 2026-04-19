package com.jpd.finsync.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: SyncedTrack)

    @Query("SELECT * FROM synced_tracks WHERE itemId = :itemId LIMIT 1")
    suspend fun getTrack(itemId: String): SyncedTrack?

    @Query("SELECT localPath FROM synced_tracks")
    suspend fun getAllLocalPaths(): List<String>

    @Query("DELETE FROM synced_tracks WHERE localPath = :localPath")
    suspend fun deleteByLocalPath(localPath: String)

    @Query("DELETE FROM synced_tracks")
    suspend fun deleteAllTracks()

    @Query("SELECT COUNT(*) FROM synced_tracks")
    suspend fun trackCount(): Int

    @Query("SELECT COUNT(*) FROM synced_tracks WHERE albumId = :albumId")
    suspend fun getSyncedTrackCountForAlbum(albumId: String): Int

    @Query("SELECT * FROM synced_tracks WHERE albumId = :albumId")
    suspend fun getTracksForAlbum(albumId: String): List<SyncedTrack>

    @Query("DELETE FROM synced_tracks WHERE albumId = :albumId")
    suspend fun deleteTracksForAlbum(albumId: String)

    @Query("SELECT MAX(syncedAt) FROM synced_tracks WHERE albumId = :albumId")
    suspend fun getLastSyncedTimeForAlbum(albumId: String): Long?

    // ── Album metadata ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbum(album: SyncedAlbum)

    @Query("SELECT * FROM synced_albums ORDER BY name")
    suspend fun getAllAlbums(): List<SyncedAlbum>

    @Query("SELECT * FROM synced_albums WHERE albumId = :albumId LIMIT 1")
    suspend fun getAlbum(albumId: String): SyncedAlbum?

    @Query("UPDATE synced_albums SET artworkPath = :path WHERE albumId = :albumId")
    suspend fun setAlbumArtwork(albumId: String, path: String)

    @Query("DELETE FROM synced_albums WHERE albumId = :albumId")
    suspend fun deleteAlbum(albumId: String)

    @Query("DELETE FROM synced_albums")
    suspend fun deleteAllAlbums()

    @Query("DELETE FROM synced_albums WHERE albumId NOT IN (SELECT DISTINCT albumId FROM synced_tracks WHERE albumId IS NOT NULL)")
    suspend fun deleteAlbumsWithNoTracks()
}
