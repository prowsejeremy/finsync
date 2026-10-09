package com.jpd.hz.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.hz.appearance.Accent
import com.jpd.hz.appearance.AppearanceStore
import com.jpd.hz.appearance.ThemeMode
import com.jpd.hz.auth.JellyfinRepository
import com.jpd.hz.auth.Result
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.library.SyncSelections
import com.jpd.hz.model.AlbumSelection
import com.jpd.hz.model.ServerConfig
import com.jpd.hz.service.SyncScheduler
import com.jpd.hz.sync.BookChoice
import com.jpd.hz.sync.JellyfinCatalogue
import com.jpd.hz.sync.PlaylistChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG = "SettingsViewModel"

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = JellyfinRepository(app)
    private val dao  = SyncDatabase.getInstance(app).syncDao()
    private val catalogue = JellyfinCatalogue(app)
    private val selections = SyncSelections(app)
    private val appearance = AppearanceStore(app)
    private var catalogueRefreshed = false

    /** Every audio playlist on the server, from the catalogue (3b). */
    val playlistChoices: LiveData<List<PlaylistChoice>> = catalogue.playlistChoices().asLiveData()

    /** Every audiobook on the server, from the catalogue (3b). */
    val bookChoices: LiveData<List<BookChoice>> = catalogue.bookChoices().asLiveData()

    private val _albums  = MutableLiveData<List<AlbumSelection>>()
    val albums: LiveData<List<AlbumSelection>> = _albums
    private val _albumsFailed = MutableLiveData(false)
    /** The last album fetch failed, such as on a refused sign-in (spec "Sign-in health"). */
    val albumsFailed: LiveData<Boolean> = _albumsFailed
    // The sign-in the albums were loaded for: one made on the Jellyfin page loads them again (T4).
    private var albumsLoadedFor: ServerConfig? = null

    init {
        loadAlbums()
    }

    fun loadAlbums() {
        val cfg = repo.getSavedConfig() ?: return
        albumsLoadedFor = cfg
        _albumsFailed.value = false
        viewModelScope.launch {
            when (val result = repo.getAlbums(cfg)) {
                is Result.Success -> {
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
                is Result.Error -> {
                    Log.w(TAG, "Couldn't load albums: ${result.message}")
                    _albumsFailed.value = true
                }
            }
        }
    }

    /** Loads the albums unless they're already [config]'s, such as after signing in (T4). */
    fun loadAlbumsFor(config: ServerConfig) {
        if (config != albumsLoadedFor) loadAlbums()
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

    /** The playlists chosen in Playlists to Sync; empty means none (3b). */
    fun getSelectedPlaylistIds(): Set<String> = selections.playlistIds()

    fun setSelectedPlaylistIds(ids: Set<String>) = selections.setPlaylistIds(ids)

    /** The books chosen in Books to Sync; empty means none (3b). */
    fun getSelectedBookIds(): Set<String> = selections.bookIds()

    fun setSelectedBookIds(ids: Set<String>) = selections.setBookIds(ids)

    /**
     * Refreshes the catalogue once per visit to Settings, so playlists and books added on the
     * server show in the choice screens. Offline, the screens show what's cached.
     */
    fun refreshCatalogueOnce() {
        if (catalogueRefreshed) return
        catalogueRefreshed = true
        viewModelScope.launch {
            try {
                if (!catalogue.refresh()) Log.w(TAG, "Catalogue refresh didn't complete")
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

    // Appearance's choices (spec "Settings screens"); AppearanceFragment applies them.

    fun themeMode(): ThemeMode = appearance.themeMode()

    fun setThemeMode(mode: ThemeMode) = appearance.setThemeMode(mode)

    fun accent(): Accent = appearance.accent()

    fun setAccent(accent: Accent) = appearance.setAccent(accent)
}
