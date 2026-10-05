package com.jpd.finsync.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.library.BookRepository
import com.jpd.finsync.library.LibraryRepository
import com.jpd.finsync.library.PlaylistRepository
import com.jpd.finsync.library.SyncSelections
import com.jpd.finsync.model.ServerConfig
import com.jpd.finsync.model.SyncState
import com.jpd.finsync.playback.ResumeStore
import com.jpd.finsync.service.SyncScheduler
import com.jpd.finsync.service.SyncService
import com.jpd.finsync.sync.SyncCounts
import com.jpd.finsync.sync.SyncEngine
import com.jpd.finsync.sync.SyncSelection
import com.jpd.finsync.sync.syncCountsOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "MainViewModel"

class MainViewModel(app: Application) : AndroidViewModel(app) {
    fun checkServerConnection() {
        val cfg = _config.value ?: return
        viewModelScope.launch {
            val healthy = repo.isServerHealthy(cfg.serverUrl)
            // isServerHealthy() returns false when cancelled, which mustn't stop a running sync.
            ensureActive()
            _serverConnected.postValue(healthy)
            // Only a running sync needs stopping; stopping an idle one would mark it "stopped".
            if (!healthy && SyncEngine.syncState.value.isRunning) stopSync()
        }
    }

    private val repo = JellyfinRepository(app)
    private val dao = SyncDatabase.getInstance(app).syncDao()
    private val catalogueDao = SyncDatabase.getInstance(app).catalogueDao()

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

    private val _syncDir   = MutableLiveData<String>()
    val syncDir: LiveData<String> = _syncDir

    data class UiState(
        val syncState: SyncState? = null,
        val syncCounts: SyncCounts = SyncCounts.NONE,
        val serverConnected: Boolean = true
    )

    // Counts first: when the screen comes back, they're delivered before the sync state too.
    private val _uiState = MediatorLiveData<UiState>().apply {
        addSource(_syncCounts)      { value = (value ?: UiState()).copy(syncCounts = it) }
        addSource(_syncState)       { value = (value ?: UiState()).copy(syncState = it) }
        addSource(_serverConnected) { value = (value ?: UiState()).copy(serverConnected = it) }
    }
    val uiState: LiveData<UiState> = _uiState

    init {
        refreshConfig()
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
        _config.value = cfg
        if (cfg != null) {
            _syncDir.value = SyncEngine.getSyncDirectoryPath(getApplication(), cfg)
            refreshSyncCounts()
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

    fun isLoggedIn() = repo.isLoggedIn()

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

    fun logout() {
        viewModelScope.launch {
            // So a different server's library and queue aren't shown after the next login.
            // The activity finishes straight after this call, so logging out and clearing the
            // queue and catalogue must all finish even if this scope is cancelled.
            withContext(NonCancellable) {
                repo.logout(getApplication())
                ResumeStore(getApplication()).clear()
                val library = LibraryRepository(getApplication())
                library.clearCatalogue()
                library.deleteArtistPhotos()
                PlaylistRepository(getApplication()).deletePlaylistCovers()
                BookRepository(getApplication()).clearBookProgress()
            }
            _config.postValue(null)
        }
    }

    fun getSyncDirectoryPath(): String? {
        val cfg = _config.value ?: return null
        return SyncEngine.getSyncDirectoryPath(getApplication(), cfg)
    }

    fun setSyncDirectory(path: String) {
        getApplication<Application>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("sync_directory", path)
            .apply()
        val cfg = _config.value ?: return
        _syncDir.value = SyncEngine.getSyncDirectoryPath(getApplication(), cfg)
    }
}
