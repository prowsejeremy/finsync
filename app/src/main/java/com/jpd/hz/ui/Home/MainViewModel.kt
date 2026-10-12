package com.jpd.hz.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.adapter.folders.FolderSetup
import com.jpd.hz.adapter.run.Catalogue
import com.jpd.hz.adapter.run.SyncState
import com.jpd.hz.adapter.run.SyncStates
import com.jpd.hz.adapter.service.SyncScheduler
import com.jpd.hz.library.scan.LibraryScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val TAG = "MainViewModel"

/**
 * The shell's activity-wide state (spec "Running connections"): the launch settle and scan, a
 * catalogue for each connection whose catalogue is empty, availability checks on network changes,
 * and the running sync for Home's ring. Each connection's page has its own ConnectionViewModel.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val catalogue = Catalogue(app)

    /** The running connection's sync, for Home's Settings ring; null when none runs. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val runningSync: LiveData<SyncState?> = SyncStates.running
        .flatMapLatest { id -> id?.let(SyncStates::of) ?: flowOf(null) }
        .asLiveData()

    init {
        // The app opens: a change of Library folder cut short is finished (T3), then the player
        // scans it.
        viewModelScope.launch {
            try {
                FolderSetup(app).settleAtLaunch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Files and Room; the next launch tries again, and the scan still runs.
                Log.w(TAG, "Couldn't settle the Library folder", e)
            }
            LibraryScanner.get(app).requestScan()
        }
        // The one schedule from before the harness; each connection schedules its own now.
        SyncScheduler.cancelLegacy(app)
        refreshEmptyCatalogues()
    }

    /** Checks every connection's source: at launch, and when the network changes. */
    fun checkConnections() {
        for (connection in Platforms.connections()) {
            viewModelScope.launch {
                ConnectionAvailability.check(getApplication(), connection)
            }
        }
    }

    // The Sync card and the choice screens read the catalogue, and a fresh sign-in or the
    // database's rebuild leaves it empty. No sync is needed.
    private fun refreshEmptyCatalogues() {
        for (connection in Platforms.connections()) {
            val platform = Platforms.find(connection.platform) ?: continue
            viewModelScope.launch {
                try {
                    if (catalogue.isEmpty(connection.id)) {
                        // The same sign-in, user and all, as the fetch was made with (T4).
                        catalogue.refresh(connection.id, platform.source(connection)) {
                            Platforms.connection(connection.id) == connection
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Room and bad server data throw too, not only network errors.
                    Log.w(TAG, "Couldn't build ${connection.name}'s catalogue", e)
                }
            }
        }
    }
}
