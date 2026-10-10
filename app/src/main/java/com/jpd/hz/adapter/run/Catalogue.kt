package com.jpd.hz.adapter.run

import android.content.Context
import android.util.Log
import com.jpd.hz.adapter.CatalogueResult
import com.jpd.hz.adapter.ChoiceGroup
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.ItemKind
import com.jpd.hz.adapter.Source
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.db.CatalogueDao
import com.jpd.hz.adapter.db.CatalogueGroup
import com.jpd.hz.adapter.db.CatalogueGroupItem
import com.jpd.hz.adapter.db.CatalogueItem
import com.jpd.hz.adapter.db.SyncDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "Catalogue"

/** A group in a choice screen, with how many items it holds and their size. */
data class GroupChoice(
    val groupId: String,
    val name: String,
    val detail: String?,
    val itemCount: Int,
    val size: Long?
)

/**
 * Each connection's copy of its source's catalogue (spec "Data"): what its sync plans from, what
 * its Sync card counts and what its choice screens list. Written before any download, so a
 * cancelled sync still leaves it current.
 */
class Catalogue internal constructor(private val dao: CatalogueDao) {

    constructor(context: Context) : this(
        SyncDatabase.getInstance(context.applicationContext).catalogueDao()
    )

    /**
     * Replaces [connectionId]'s catalogue with [fresh] in one transaction; a failed part keeps its
     * previous rows. Returns what was stored, which the run plans from.
     */
    suspend fun write(connectionId: String, fresh: SourceCatalogue): StoredCatalogue {
        // Read just before the swap; a refresh racing a sync writes the same source's data.
        val merged = mergeFailedParts(fresh, stored(connectionId))
        val rows = rowsOf(connectionId, merged)
        dao.replace(connectionId, rows.items, rows.groups, rows.groupItems)
        return merged
    }

    /**
     * Fetches [source]'s catalogue and stores it, without downloading, while [stillSignedIn]
     * holds: a sign-out during the fetch has cleared the catalogue, and this write mustn't bring
     * it back (T4). False when nothing was written.
     */
    suspend fun refresh(
        connectionId: String,
        source: Source,
        stillSignedIn: () -> Boolean
    ): Boolean = when (val result = source.catalogue()) {
        is CatalogueResult.Success -> {
            currentCoroutineContext().ensureActive()
            writeWhileSignedIn(connectionId, result.catalogue, stillSignedIn)
        }
        is CatalogueResult.Failure -> {
            Log.w(TAG, "Catalogue refresh failed: ${result.message}")
            false
        }
    }

    internal suspend fun writeWhileSignedIn(
        connectionId: String,
        catalogue: SourceCatalogue,
        stillSignedIn: () -> Boolean
    ): Boolean = writeLock.withLock {
        if (!stillSignedIn()) return@withLock false
        write(connectionId, catalogue)
        true
    }

    /**
     * Sign-out: nothing is cleared once [signedIn] says a new sign-in has come (T4), because the
     * new sign-in's refresh may already have written its own catalogue.
     */
    suspend fun clear(connectionId: String, signedIn: () -> Boolean = { false }) =
        writeLock.withLock { if (!signedIn()) dao.clear(connectionId) }

    suspend fun isEmpty(connectionId: String): Boolean = dao.itemCount(connectionId) == 0

    suspend fun stored(connectionId: String): StoredCatalogue =
        storedOf(dao.items(connectionId), dao.groups(connectionId), dao.groupItems(connectionId))

    /** One kind's groups, A–Z, for its choice screen and its row's summary. */
    fun choices(connectionId: String, kind: ChoiceKind): Flow<List<GroupChoice>> =
        dao.observeChoices(connectionId, kind.key)
            .map { rows ->
                rows.map { GroupChoice(it.groupId, it.name, it.detail, it.itemCount, it.size) }
            }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    companion object {
        // A refresh's check and write, and sign-out's clear, one at a time. A sync's write needs
        // none of it: sign-out waits for the sync's lock before clearing.
        private val writeLock = Mutex()
    }
}

/** The rows [stored] writes for [connectionId]. */
internal fun rowsOf(connectionId: String, stored: StoredCatalogue): CatalogueRowSet =
    CatalogueRowSet(
        items = stored.items.mapIndexed { position, item ->
            CatalogueItem(
                connectionId, item.id, position, item.kind.name, item.label, item.durationMs,
                item.size
            )
        },
        groups = stored.groups.mapIndexed { position, group ->
            CatalogueGroup(
                connectionId, group.kind.key, group.id, position, group.name, group.detail
            )
        },
        groupItems = stored.groups.flatMap { group ->
            group.itemIds.mapIndexed { position, itemId ->
                CatalogueGroupItem(connectionId, group.kind.key, group.id, position, itemId)
            }
        }
    )

/** The stored catalogue from its rows. A row of a kind this build doesn't know is skipped. */
internal fun storedOf(
    items: List<CatalogueItem>,
    groups: List<CatalogueGroup>,
    groupItems: List<CatalogueGroupItem>
): StoredCatalogue {
    val members = groupItems.groupBy { it.kind to it.groupId }
    return StoredCatalogue(
        groups = groups.mapNotNull { row ->
            val kind = ChoiceKind.fromKey(row.kind) ?: return@mapNotNull null
            val itemIds = members[row.kind to row.groupId].orEmpty()
                .sortedBy { it.position }
                .map { it.itemId }
            ChoiceGroup(row.groupId, kind, row.name, row.detail, itemIds)
        },
        items = items.mapNotNull { row ->
            val kind = ItemKind.values().firstOrNull { it.name == row.kind }
                ?: return@mapNotNull null
            StoredItem(row.itemId, kind, row.label, row.durationMs, row.size)
        }
    )
}

/** The three tables' rows for one connection. */
internal data class CatalogueRowSet(
    val items: List<CatalogueItem>,
    val groups: List<CatalogueGroup>,
    val groupItems: List<CatalogueGroupItem>
)
