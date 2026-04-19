package com.jpd.finsync.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.auth.Result
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.model.AlbumSelection
import com.jpd.finsync.model.ServerConfig
import com.jpd.finsync.model.SyncState
import com.jpd.finsync.service.SyncScheduler
import com.jpd.finsync.service.SyncService
import com.jpd.finsync.sync.SyncEngine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    fun checkServerConnection() {
        val cfg = _config.value ?: return
        viewModelScope.launch {
            val healthy = repo.isServerHealthy(cfg.serverUrl)
            _serverConnected.postValue(healthy)
        }
    }

    private val repo = JellyfinRepository(app)
    private val dao = SyncDatabase.getInstance(app).syncDao()

    private val _serverConnected = MutableLiveData<Boolean>(true)
    // val serverConnected: LiveData<Boolean> = _serverConnected

    // /** (syncedTracks, totalSelectedTracks) — updated whenever album selection or DB changes. */
    private val _trackStats = MutableLiveData(Pair(0, 0))
    // val trackStats: LiveData<Pair<Int, Int>> = _trackStats

    private val _syncState = MutableLiveData<SyncState>()
    // val syncState: LiveData<SyncState> = _syncState

    private val _config    = MutableLiveData<ServerConfig?>()
    val config: LiveData<ServerConfig?> = _config

    private val _syncDir   = MutableLiveData<String>()
    val syncDir: LiveData<String> = _syncDir

    private val _albums = MutableLiveData<List<AlbumSelection>>()
    val albums: LiveData<List<AlbumSelection>> = _albums

    data class UiState(
        val syncState: SyncState? = null,
        val trackStats: Pair<Int, Int> = Pair(0, 0),
        val serverConnected: Boolean = true
    )

    private val _uiState = MediatorLiveData<UiState>().apply {
        addSource(_syncState)       { value = (value ?: UiState()).copy(syncState = it) }
        addSource(_trackStats)      { value = (value ?: UiState()).copy(trackStats = it) }
        addSource(_serverConnected) { value = (value ?: UiState()).copy(serverConnected = it) }
    }
    val uiState: LiveData<UiState> = _uiState

    init {
        refreshConfig()
        viewModelScope.launch {
            SyncEngine.syncState.collectLatest { state ->
                _syncState.postValue(state)
                if (state.syncComplete) loadAlbums()
            }
        }
    }

    fun refreshConfig() {
        val cfg = repo.getSavedConfig()
        _config.value = cfg
        if (cfg != null) {
            _syncDir.value = SyncEngine.getSyncDirectoryPath(getApplication(), cfg)
            loadAlbums()
        }
    }

    fun loadAlbums() {
        val cfg = _config.value ?: return
        viewModelScope.launch {
            val result = repo.getAlbums(cfg)
            if (result is Result.Success) {
                val selectedIds = getSelectedAlbumIds()
                val isAll = selectedIds.isEmpty() || selectedIds.contains("all")

                // Single pass: collect synced count per album to avoid duplicate DB queries
                data class AlbumRow(val album: com.jpd.finsync.model.MediaItem, val synced: Int)
                val rows = result.data.items.map { album ->
                    AlbumRow(album, dao.getSyncedTrackCountForAlbum(album.id))
                }

                val albumList = rows.map { (album, synced) ->
                    val isDownloaded = (album.childCount ?: 0) > 0 && synced >= album.childCount!!
                    AlbumSelection(item = album, isSelected = selectedIds.contains(album.id) || isDownloaded, isDownloaded = isDownloaded)
                }.sortedBy { it.item.name }
                _albums.postValue(albumList)

                // Track stats: only count albums that are in the current selection
                val selectedRows = if (isAll) rows else rows.filter { selectedIds.contains(it.album.id) }
                val totalTracks  = selectedRows.sumOf { it.album.childCount ?: 0 }
                val syncedTracks = selectedRows.sumOf { it.synced }
                _trackStats.postValue(Pair(syncedTracks, totalTracks))
            }
        }
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

    fun scheduleAutoSync(intervalHours: Long = 6) =
        SyncScheduler.schedulePeriodicSync(getApplication(), intervalHours)

    fun cancelAutoSync() = SyncScheduler.cancelPeriodicSync(getApplication())

    fun logout() {
        viewModelScope.launch {
            repo.logout(getApplication())
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
