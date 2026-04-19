package com.jpd.finsync.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.auth.Result
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.model.AlbumSelection
import com.jpd.finsync.service.SyncScheduler
import com.jpd.finsync.sync.SyncEngine
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JellyfinRepository(app)
    private val dao  = SyncDatabase.getInstance(app).syncDao()

    private val _albums  = MutableLiveData<List<AlbumSelection>>()
    val albums: LiveData<List<AlbumSelection>> = _albums

    private val _syncDir = MutableLiveData<String>()
    val syncDir: LiveData<String> = _syncDir

    init {
        refreshSyncDir()
        loadAlbums()
    }

    fun loadAlbums() {
        val cfg = repo.getSavedConfig() ?: return
        viewModelScope.launch {
            val result = repo.getAlbums(cfg)
            if (result is Result.Success) {
                val selectedIds = getSelectedAlbumIds()
                val albumList = result.data.items.map { album ->
                    val syncedCount = dao.getSyncedTrackCountForAlbum(album.id)
                    val isDownloaded = album.childCount != null &&
                            album.childCount > 0 &&
                            syncedCount >= album.childCount
                    AlbumSelection(
                        item        = album,
                        isSelected  = selectedIds.contains("all") ||
                                      selectedIds.contains(album.id) ||
                                      isDownloaded,
                        isDownloaded = isDownloaded
                    )
                }.sortedBy { it.item.name }
                _albums.postValue(albumList)
            }
        }
    }

    fun refreshSyncDir() {
        val cfg = repo.getSavedConfig() ?: return
        _syncDir.value = SyncEngine.getSyncDirectoryPath(getApplication(), cfg)
    }

    fun getSelectedAlbumIds(): Set<String> =
        getApplication<Application>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getStringSet("selected_albums", emptySet()) ?: emptySet()

    fun setSelectedAlbumIds(ids: Set<String>) {
        getApplication<Application>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("selected_albums", ids)
            .apply()
    }

    fun getAutoSyncInterval(): String =
        getApplication<Application>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("auto_sync_interval", "disabled") ?: "disabled"

    fun setAutoSyncInterval(interval: String) {
        getApplication<Application>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("auto_sync_interval", interval)
            .apply()
        when (interval) {
            "disabled" -> SyncScheduler.cancelPeriodicSync(getApplication())
            else       -> SyncScheduler.schedulePeriodicSync(getApplication(), interval.toLong())
        }
    }

    fun getSyncDirectoryPath(): String? {
        val cfg = repo.getSavedConfig() ?: return null
        return SyncEngine.getSyncDirectoryPath(getApplication(), cfg)
    }

    fun setSyncDirectory(path: String) {
        getApplication<Application>()
            .getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("sync_directory", path)
            .apply()
        refreshSyncDir()
    }
}
