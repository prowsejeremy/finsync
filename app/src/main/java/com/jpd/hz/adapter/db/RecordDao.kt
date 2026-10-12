package com.jpd.hz.adapter.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Each connection's sync records; every query names its connection (spec "Data"). */
@Dao
interface RecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(file: SyncedFile)

    @Query("SELECT * FROM synced_files WHERE connectionId = :connectionId AND itemId = :itemId")
    suspend fun get(connectionId: String, itemId: String): SyncedFile?

    /** The connection's synced items, for the Sync card's counts. */
    @Query("SELECT itemId FROM synced_files WHERE connectionId = :connectionId")
    suspend fun itemIds(connectionId: String): List<String>

    @Query("SELECT itemId, path FROM synced_files WHERE connectionId = :connectionId")
    suspend fun paths(connectionId: String): List<RecordPath>

    /** The connection's records by path and size, to recognise its folder after a move (A4). */
    @Query("SELECT path, fileSize FROM synced_files WHERE connectionId = :connectionId")
    suspend fun sizes(connectionId: String): List<RecordSize>

    @Query("SELECT COUNT(*) FROM synced_files WHERE connectionId = :connectionId")
    suspend fun count(connectionId: String): Int

    @Query("DELETE FROM synced_files WHERE connectionId = :connectionId AND path = :path")
    suspend fun deleteByPath(connectionId: String, path: String)
}
