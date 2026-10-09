package com.jpd.hz.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.hz.api.SignInStatus
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.library.SyncSelections
import com.jpd.hz.library.scan.LibraryScanner
import com.jpd.hz.model.ServerConfig
import com.jpd.hz.model.SyncState
import com.jpd.hz.service.SyncScheduler
import com.jpd.hz.service.SyncService
import com.jpd.hz.sync.FolderSetup
import com.jpd.hz.sync.JellyfinCatalogue
import com.jpd.hz.sync.JellyfinSignOut
import com.jpd.hz.sync.SyncCounts
import com.jpd.hz.sync.SyncEngine
import com.jpd.hz.sync.SyncSelection
import com.jpd.hz.sync.syncCountsOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "MainViewModel"

class MainViewModel(app: Application) : AndroidViewModel(app) {
    fun checkServerConnection() {
        val cfg = _config.value ?: return
        viewModelScope.launch {
            // checkServer rethrows cancellation, so a cancelled check posts nothing.
            val check = repo.checkServer(cfg)
            _serverConnected.postValue(check.reachable)
            // Only a running sync needs stopping; stopping an idle one would mark it "stopped".
            if (!check.reachable && SyncEngine.syncState.value.isRunning) stopSync()
        }
    }

    private val repo = JellyfinRepository(app)
    private val dao = SyncDatabase.getInstance(app).syncDao()
    private val catalogueDao = SyncDatabase.getInstance(app).catalogueDao()
    private val catalogue = JellyfinCatalogue(app)

    private val _serverConnected = MutableLiveData<Boolean>(true)
    // val serverConnected: LiveData<Boolean> = _serverConnected

    // The Sync card's counts, from the stored catalogue (spec "Counts").
    private val _syncCounts = MutableLiveData(SyncCounts.NONE)
    // Refreshes run one at a time, so an older read can't land after a newer one.
    private val syncCountsLock = Mutex()

    private val _syncState = MutableLiveData<SyncState>()
    // val syncState: LiveData<SyncState> = _syncState

    private val _config    = MutableLiveData<ServerConfig?>()
    val config: LiveData<ServerConfig?> = _config

    data class UiState(
        val syncState: SyncState? = null,
        val syncCounts: SyncCounts = SyncCounts.NONE,
        val serverConnected: Boolean = true,
        /** The server refuses the saved sign-in (spec "Sign-in health", decision 2). */
        val signInRefused: Boolean = false
    )

    // Counts first: when the screen comes back, they're delivered before the sync state too.
    private val _uiState = MediatorLiveData<UiState>().apply {
        addSource(_syncCounts)      { value = (value ?: UiState()).copy(syncCounts = it) }
        addSource(_syncState)       { value = (value ?: UiState()).copy(syncState = it) }
        addSource(_serverConnected) { value = (value ?: UiState()).copy(serverConnected = it) }
        addSource(SignInStatus.shared.refusedToken.asLiveData()) { showSignInRefused() }
        addSource(_config) { showSignInRefused() }
    }

    private fun MediatorLiveData<UiState>.showSignInRefused() {
        val refused = SignInStatus.isRefused(
            SignInStatus.shared.refusedToken.value,
            _config.value?.accessToken
        )
        value = (value ?: UiState()).copy(signInRefused = refused)
    }

    val uiState: LiveData<UiState> = _uiState

    /** Jellyfin as Settings → Adapters shows it (D12). */
    val jellyfin: Adapter = JellyfinAdapter(app, this)

    init {
        refreshConfig()
        // The app opens: the Library folder is settled once (T3), then the player scans it.
        viewModelScope.launch {
            try {
                FolderSetup(app).settleAtLaunch(repo.getSavedConfig())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Files and Room; the next launch tries again, and the scan still runs.
                Log.w(TAG, "Couldn't settle the Library folder", e)
            }
            LibraryScanner.get(app).requestScan()
        }
        viewModelScope.launch {
            // An ended sync (completed, stopped or failed) recounts before its state is posted,
            // so the card doesn't flash "Not synced yet" (spec "MainViewModel"). This scope runs
            // on the main thread, so both values are set in that order.
            relaySyncStates(
                SyncEngine.syncState,
                refreshCounts = { updateSyncCounts() },
                post = { _syncState.value = it }
            )
        }
    }

    fun refreshConfig() {
        val cfg = repo.getSavedConfig()
        // Signed in since the last read, on the Jellyfin page (T4); not the first read at launch.
        val newSignIn = cfg != null && _config.isInitialized && cfg != _config.value
        _config.value = cfg
        if (cfg != null) {
            refreshSyncCounts()
            refreshCatalogue(onlyIfEmpty = !newSignIn)
            // The last check, if any, was made signed out or for another sign-in.
            if (newSignIn) checkServerConnection()
        }
    }

    // Home built the catalogue on first open until T3: the Sync card and the choice screens
    // read it, and a fresh sign-in or a database rebuild leaves it empty. No sync is needed. A
    // new sign-in always refreshes: a sign-out cut short may have left the last server's.
    private fun refreshCatalogue(onlyIfEmpty: Boolean) {
        viewModelScope.launch {
            try {
                if ((!onlyIfEmpty || catalogue.isEmpty()) && catalogue.refresh()) updateSyncCounts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Room and bad server data throw too, not only network errors.
                Log.w(TAG, "Couldn't build the catalogue", e)
            }
        }
    }

    /** Recounts the Sync card from the stored catalogue and selections; no server call. */
    fun refreshSyncCounts() {
        viewModelScope.launch { updateSyncCounts() }
    }

    private suspend fun updateSyncCounts() {
        syncCountsLock.withLock {
            try {
                _syncCounts.value = loadSyncCounts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The card keeps its last counts; a failed read mustn't stop the sync states.
                Log.w(TAG, "Couldn't count the synced items", e)
            }
        }
    }

    // syncPlanOf's rules over what the last catalogue write stored (spec "Counts").
    private suspend fun loadSyncCounts(): SyncCounts {
        val selections = SyncSelections(getApplication())
        return syncCountsOf(
            tracks = catalogueDao.trackAlbums(),
            playlistItems = catalogueDao.allPlaylistItems(),
            bookIds = catalogueDao.allBooks().mapTo(HashSet()) { it.bookId },
            syncedIds = dao.allItemIds().toHashSet(),
            selection = SyncSelection(
                getSelectedAlbumIds(),
                selections.playlistIds(),
                selections.bookIds()
            )
        )
    }

    fun getSelectedAlbumIds(): Set<String> {
        return getApplication<Application>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getStringSet("selected_albums", emptySet()) ?: emptySet()
    }

    fun setSelectedAlbumIds(ids: Set<String>) {
        getApplication<Application>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("selected_albums", ids)
            .apply()
    }

    fun startSync() {
        _config.value ?: return
        val intent = Intent(getApplication(), SyncService::class.java).apply {
            action = SyncService.ACTION_START
        }
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun stopSync() {
        SyncEngine.emitStopped()
        repo.cancelAudioDownload()
        val intent = Intent(getApplication(), SyncService::class.java).apply {
            action = SyncService.ACTION_STOP
        }
        getApplication<Application>().startService(intent)
    }

    fun toggleSync() {
        if (SyncEngine.syncState.value.isRunning) stopSync() else startSync()
    }

    fun scheduleAutoSync(intervalHours: Long = 6) =
        SyncScheduler.schedulePeriodicSync(getApplication(), intervalHours)

    fun cancelAutoSync() = SyncScheduler.cancelPeriodicSync(getApplication())

    /**
     * Signs out of Jellyfin (spec "Signing out", amended 2026-10-09). The screens show it at once;
     * the clearing goes on in the background. The player isn't touched: playback, the queue and
     * book progress stay (D10).
     */
    fun signOut() {
        _config.value = null
        viewModelScope.launch {
            // It waits for a running sync to stop, so it must finish even if Settings closes.
            withContext(NonCancellable) { signOutQuietly() }
        }
    }

    // A failure here leaves the catalogue, which the next sign-in's refresh replaces; it mustn't
    // crash the app.
    private suspend fun signOutQuietly() {
        try {
            JellyfinSignOut(getApplication()).run(SyncEngine.syncState, ::stopSync)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't finish signing out of Jellyfin", e)
        }
    }
}
