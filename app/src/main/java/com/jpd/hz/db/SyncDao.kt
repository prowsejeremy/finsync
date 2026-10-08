package com.jpd.hz.db

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

    /** Every downloaded item, books included, for the Sync card's counts (3b refinements). */
    @Query("SELECT itemId FROM synced_tracks")
    suspend fun allItemIds(): List<String>

    /**
     * Records saved with full paths before T3 become relative to [folder] (its path and a "/"),
     * as A1 saves them. Run once, with [deleteFullPathTracks] after it.
     */
    @Query(
        "UPDATE synced_tracks SET localPath = substr(localPath, length(:folder) + 1) " +
            "WHERE substr(localPath, 1, length(:folder)) = :folder"
    )
    suspend fun makeTrackPathsRelative(folder: String)

    /** Full-path records outside the sync folder: their files aren't the adapter's any more. */
    @Query("DELETE FROM synced_tracks WHERE substr(localPath, 1, 1) = '/'")
    suspend fun deleteFullPathTracks()

    /** The signed-in server's records: their items are in the catalogue, which is the server's. */
    @Query(
        "SELECT COUNT(*) FROM synced_tracks " +
            "WHERE itemId IN (SELECT itemId FROM catalogue_tracks) " +
            "OR itemId IN (SELECT bookId FROM catalogue_books)"
    )
    suspend fun countServerRecords(): Int

    /** Every record by path and size, when there's no catalogue to pick the server's from. */
    @Query("SELECT localPath, fileSize FROM synced_tracks")
    suspend fun allRecordSizes(): List<RecordSize>

    /** The signed-in server's records, by path and size, to recognise its folder after a move. */
    @Query(
        "SELECT localPath, fileSize FROM synced_tracks " +
            "WHERE itemId IN (SELECT itemId FROM catalogue_tracks) " +
            "OR itemId IN (SELECT bookId FROM catalogue_books)"
    )
    suspend fun serverRecordSizes(): List<RecordSize>

    /** The signed-in server's records' paths, for the sync's record pass (T4). */
    @Query(
        "SELECT localPath FROM synced_tracks " +
            "WHERE itemId IN (SELECT itemId FROM catalogue_tracks) " +
            "OR itemId IN (SELECT bookId FROM catalogue_books)"
    )
    suspend fun serverLocalPaths(): List<String>

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

    // ── Album metadata ─────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbum(album: SyncedAlbum)

    @Query("SELECT * FROM synced_albums WHERE albumId = :albumId LIMIT 1")
    suspend fun getAlbum(albumId: String): SyncedAlbum?

    @Query("UPDATE synced_albums SET artworkPath = :path WHERE albumId = :albumId")
    suspend fun setAlbumArtwork(albumId: String, path: String)

    /** Album art saved with a full path before T3 becomes relative to [folder], or goes. */
    @Query(
        "UPDATE synced_albums SET artworkPath = CASE " +
            "WHEN substr(artworkPath, 1, length(:folder)) = :folder " +
            "THEN substr(artworkPath, length(:folder) + 1) ELSE NULL END " +
            "WHERE substr(artworkPath, 1, 1) = '/'"
    )
    suspend fun makeArtworkPathsRelative(folder: String)

    @Query("DELETE FROM synced_albums")
    suspend fun deleteAllAlbums()

    @Query("DELETE FROM synced_albums WHERE albumId NOT IN (SELECT DISTINCT albumId FROM synced_tracks WHERE albumId IS NOT NULL)")
    suspend fun deleteAlbumsWithNoTracks()
}

/** A record's path, relative to its sync folder, and the file's size after tagging. */
data class RecordSize(val localPath: String, val fileSize: Long)
