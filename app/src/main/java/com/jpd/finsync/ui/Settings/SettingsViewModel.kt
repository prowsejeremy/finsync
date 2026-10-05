package com.jpd.finsync.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.finsync.appearance.Accent
import com.jpd.finsync.appearance.AppearanceStore
import com.jpd.finsync.appearance.ThemeMode
import com.jpd.finsync.auth.JellyfinRepository
import com.jpd.finsync.auth.Result
import com.jpd.finsync.db.SyncDatabase
import com.jpd.finsync.library.BookChoice
import com.jpd.finsync.library.BookRepository
import com.jpd.finsync.library.LibraryRepository
import com.jpd.finsync.library.PlaylistChoice
import com.jpd.finsync.library.PlaylistRepository
import com.jpd.finsync.model.AlbumSelection
import com.jpd.finsync.service.SyncScheduler
import com.jpd.finsync.sync.SyncEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "SettingsViewModel"

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JellyfinRepository(app)
    private val dao  = SyncDatabase.getInstance(app).syncDao()
    private val library = LibraryRepository(app)
    private val playlists = PlaylistRepository(app)
    private val books = BookRepository(app)
    private val appearance = AppearanceStore(app)
    private var catalogueRefreshed = false

    /** Every audio playlist on the server, from the catalogue (3b). */
    val playlistChoices: LiveData<List<PlaylistChoice>> = playlists.playlistChoices().asLiveData()

    /** Every audiobook on the server, from the catalogue (3b). */
    val bookChoices: LiveData<List<BookChoice>> = books.bookChoices().asLiveData()

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

    fun getSelectedPlaylistIds(): Set<String> = playlists.selectedIds()

    fun setSelectedPlaylistIds(ids: Set<String>) = playlists.setSelectedIds(ids)

    fun getSelectedBookIds(): Set<String> = books.selectedIds()

    fun setSelectedBookIds(ids: Set<String>) = books.setSelectedIds(ids)

    /**
     * Refreshes the catalogue once per visit to Settings, so playlists and books added on the
     * server show in the choice screens. Offline, the screens show what's cached.
     */
    fun refreshCatalogueOnce() {
        if (catalogueRefreshed) return
        catalogueRefreshed = true
        viewModelScope.launch {
            try {
                if (!library.refreshCatalogue()) Log.w(TAG, "Catalogue refresh didn't complete")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't refresh the catalogue", e)
            }
        }
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

    // Appearance's choices (spec "Settings screens"); AppearanceFragment applies them.

    fun themeMode(): ThemeMode = appearance.themeMode()

    fun setThemeMode(mode: ThemeMode) = appearance.setThemeMode(mode)

    fun accent(): Accent = appearance.accent()

    fun setAccent(accent: Accent) = appearance.setAccent(accent)
}
