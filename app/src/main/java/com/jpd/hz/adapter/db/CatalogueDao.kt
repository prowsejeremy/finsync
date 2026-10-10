package com.jpd.hz.adapter.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Each connection's copy of its source's catalogue: what its sync plans from, its Sync card
 * counts and its choice screens list. A write touches only its own connection's rows.
 */
@Dao
abstract class CatalogueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertItems(items: List<CatalogueItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertGroups(groups: List<CatalogueGroup>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertGroupItems(items: List<CatalogueGroupItem>)

    @Query("DELETE FROM catalogue_items WHERE connectionId = :connectionId")
    abstract suspend fun deleteItems(connectionId: String)

    @Query("DELETE FROM catalogue_groups WHERE connectionId = :connectionId")
    abstract suspend fun deleteGroups(connectionId: String)

    @Query("DELETE FROM catalogue_group_items WHERE connectionId = :connectionId")
    abstract suspend fun deleteGroupItems(connectionId: String)

    /** Swaps one connection's rows in one transaction, so readers never see half of it. */
    @Transaction
    open suspend fun replace(
        connectionId: String,
        items: List<CatalogueItem>,
        groups: List<CatalogueGroup>,
        groupItems: List<CatalogueGroupItem>
    ) {
        clear(connectionId)
        insertItems(items)
        insertGroups(groups)
        insertGroupItems(groupItems)
    }

    @Transaction
    open suspend fun clear(connectionId: String) {
        deleteGroupItems(connectionId)
        deleteGroups(connectionId)
        deleteItems(connectionId)
    }

    @Query("SELECT COUNT(*) FROM catalogue_items WHERE connectionId = :connectionId")
    abstract suspend fun itemCount(connectionId: String): Int

    @Query("SELECT * FROM catalogue_items WHERE connectionId = :connectionId ORDER BY position")
    abstract suspend fun items(connectionId: String): List<CatalogueItem>

    @Query("SELECT * FROM catalogue_groups WHERE connectionId = :connectionId ORDER BY position")
    abstract suspend fun groups(connectionId: String): List<CatalogueGroup>

    @Query(
        "SELECT * FROM catalogue_group_items WHERE connectionId = :connectionId " +
            "ORDER BY kind, groupId, position"
    )
    abstract suspend fun groupItems(connectionId: String): List<CatalogueGroupItem>

    /** One kind's groups, A–Z ignoring case, with their item counts and sizes. */
    @Query(
        """
        SELECT g.groupId AS groupId, g.name AS name, g.detail AS detail,
            COUNT(gi.itemId) AS itemCount, SUM(i.size) AS size
        FROM catalogue_groups g
        LEFT JOIN catalogue_group_items gi ON gi.connectionId = g.connectionId
            AND gi.kind = g.kind AND gi.groupId = g.groupId
        LEFT JOIN catalogue_items i ON i.connectionId = gi.connectionId AND i.itemId = gi.itemId
        WHERE g.connectionId = :connectionId AND g.kind = :kind
        GROUP BY g.groupId
        ORDER BY g.name COLLATE NOCASE, g.groupId
        """
    )
    abstract fun observeChoices(connectionId: String, kind: String): Flow<List<GroupChoiceRow>>
}
