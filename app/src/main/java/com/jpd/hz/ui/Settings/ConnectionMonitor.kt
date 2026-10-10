package com.jpd.hz.ui

import android.app.Application
import android.util.Log
import com.jpd.hz.adapter.Availability
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.adapter.choices.ChoiceStore
import com.jpd.hz.adapter.db.SyncDatabase
import com.jpd.hz.adapter.run.Catalogue
import com.jpd.hz.adapter.run.SyncCounts
import com.jpd.hz.adapter.run.SyncState
import com.jpd.hz.adapter.run.SyncStates
import com.jpd.hz.adapter.run.syncCountsOf
import com.jpd.hz.adapter.service.SyncService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ConnectionMonitor"

/**
 * Whether each connection's source answered its last check: the app's, so the page, the rows and
 * the network callback's checks agree. A connection never checked counts as reachable.
 */
object ConnectionAvailability {

    private val flags = ConcurrentHashMap<String, MutableStateFlow<Boolean>>()

    fun of(connectionId: String): StateFlow<Boolean> = flagOf(connectionId).asStateFlow()

    fun set(connectionId: String, reachable: Boolean) {
        flagOf(connectionId).value = reachable
    }

    /**
     * Checks [connection]'s source. A running sync whose source has gone away is stopped, as
     * before the harness; only its own.
     */
    suspend fun check(app: Application, connection: Connection) {
        val platform = Platforms.find(connection.platform) ?: return
        // checkAvailability rethrows cancellation, so a cancelled check posts nothing.
        val reachable =
            platform.source(connection).checkAvailability() != Availability.UNREACHABLE
        set(connection.id, reachable)
        if (!reachable && SyncStates.of(connection.id).value.isRunning) {
            SyncService.stop(app, connection.id)
        }
    }

    private fun flagOf(connectionId: String) =
        flags.getOrPut(connectionId) { MutableStateFlow(true) }
}

/**
 * One connection's live state for the screens (spec "Running connections"): its sync state, its
 * Sync card counts from the stored catalogue, whether its source answers and whether it refuses
 * the sign-in. Lives as long as [scope].
 */
class ConnectionMonitor(
    private val app: Application,
    val connection: Connection,
    private val scope: CoroutineScope
) {
    private val platform = Platforms.find(connection.platform)
    private val source = platform?.source(connection)
    private val records = SyncDatabase.getInstance(app).recordDao()
    private val catalogue = Catalogue(app)
    private val choices = ChoiceStore(app)
    private val counts = MutableStateFlow(SyncCounts.NONE)
    private val syncState = MutableStateFlow<SyncState?>(null)
    // Recounts run one at a time, so an older read can't land after a newer one.
    private val countsLock = Mutex()

    val state: StateFlow<ConnectionUiState> = combine(
        syncState,
        counts,
        ConnectionAvailability.of(connection.id),
        source?.signInRefused ?: MutableStateFlow(false)
    ) { sync, counted, reachable, refused ->
        ConnectionUiState(sync, counted, reachable, refused)
    }.stateIn(scope, SharingStarted.Eagerly, ConnectionUiState())

    init {
        scope.launch {
            // An ended sync recounts before its state is posted, so the card doesn't flash "Not
            // synced yet" (spec "MainViewModel").
            relaySyncStates(
                SyncStates.of(connection.id),
                refreshCounts = { recountNow() },
                post = { syncState.value = it }
            )
        }
        recount()
    }

    /** Recounts from the stored catalogue and choices; no server call. */
    fun recount() {
        scope.launch { recountNow() }
    }

    fun checkAvailability() {
        scope.launch { ConnectionAvailability.check(app, connection) }
    }

    private suspend fun recountNow() {
        val kinds = platform?.choiceKinds ?: return
        countsLock.withLock {
            try {
                counts.value = syncCountsOf(
                    catalogue.stored(connection.id),
                    choices.chosen(connection.id, kinds),
                    records.itemIds(connection.id).toHashSet()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The card keeps its last counts; a failed read mustn't stop the sync states.
                Log.w(TAG, "Couldn't count the synced items", e)
            }
        }
    }
}
