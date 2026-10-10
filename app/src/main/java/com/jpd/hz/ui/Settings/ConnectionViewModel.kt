package com.jpd.hz.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.Platform
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.adapter.choices.ChoiceRules
import com.jpd.hz.adapter.choices.ChoiceStore
import com.jpd.hz.adapter.folders.ConnectionFolders
import com.jpd.hz.adapter.run.Catalogue
import com.jpd.hz.adapter.run.ConnectionSignOut
import com.jpd.hz.adapter.run.GroupChoice
import com.jpd.hz.adapter.run.SyncStates
import com.jpd.hz.adapter.service.AUTO_SYNC_OFF
import com.jpd.hz.adapter.service.AutoSync
import com.jpd.hz.adapter.service.SyncService
import com.jpd.hz.library.LibraryFolderStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "ConnectionViewModel"
/** The platform page's argument: the platform's key. */
const val ARG_PLATFORM = "platform"

/**
 * One platform's page and its choice, Auto-sync and details screens (spec "Screens"), scoped to
 * `connection_graph`. It follows the platform's connection: signing in on its sign-in screen, or
 * out here, swaps the page's content.
 */
class ConnectionViewModel(
    app: Application,
    handle: SavedStateHandle
) : AndroidViewModel(app) {

    val platform: Platform = checkNotNull(Platforms.find(checkNotNull(handle[ARG_PLATFORM]))) {
        "No platform ${handle.get<String>(ARG_PLATFORM)}"
    }

    private val catalogue = Catalogue(app)
    private val choiceStore = ChoiceStore(app)
    private val autoSync = AutoSync(app)

    private val _connection = MutableLiveData<Connection?>()
    /** The platform's connection, or null while signed out. */
    val connection: LiveData<Connection?> = _connection

    private val _uiState = MediatorLiveData(ConnectionUiState())
    /** The Sync card's and the server pill's state. */
    val uiState: LiveData<ConnectionUiState> = _uiState

    private var monitor: ConnectionMonitor? = null
    private var monitorScope: CoroutineScope? = null
    private var monitorState: LiveData<ConnectionUiState>? = null
    private var catalogueRefreshed = false
    private val choiceLists = HashMap<ChoiceKind, LiveData<List<GroupChoice>>>()

    init {
        refreshConnection()
    }

    /**
     * Reads the platform's connection again: at once, and on every return to the page, such as
     * from the sign-in screen. A new sign-in refreshes the catalogue and checks the source; an
     * old one refreshes an empty catalogue, as Home did before T3.
     */
    fun refreshConnection() {
        val current = platform.connections().firstOrNull()
        val newSignIn = current != null && _connection.isInitialized && current != _connection.value
        if (current == _connection.value && _connection.isInitialized) {
            monitor?.recount()
            return
        }
        _connection.value = current
        watch(current)
        current ?: return
        refreshCatalogue(current, onlyIfEmpty = !newSignIn)
        if (newSignIn) monitor?.checkAvailability()
    }

    fun checkAvailability() {
        monitor?.checkAvailability()
    }

    /** Recounts the Sync card, after the choice screens close. */
    fun recount() {
        monitor?.recount()
    }

    fun toggleSync() {
        val id = _connection.value?.id ?: return
        val state = SyncStates.of(id).value
        if (state.isRunning) {
            SyncService.stop(getApplication(), id)
        } else {
            SyncService.start(getApplication(), id)
        }
    }

    /** One kind's groups, A–Z, from the stored catalogue. */
    fun choices(kind: ChoiceKind): LiveData<List<GroupChoice>> {
        val id = _connection.value?.id ?: return MutableLiveData(emptyList())
        return choiceLists.getOrPut(kind) { catalogue.choices(id, kind).asLiveData() }
    }

    fun chosen(kind: ChoiceKind): Set<String> =
        _connection.value?.let { choiceStore.chosen(it.id, kind) } ?: emptySet()

    /** Saves a choice screen's ticks (spec H4); nothing before its list has loaded. */
    fun saveChosen(kind: ChoiceKind, checked: Set<String>, listed: List<String>) {
        val id = _connection.value?.id ?: return
        val saved = choiceStore.chosen(id, kind)
        val ids = ChoiceRules.idsToSave(kind, checked, listed, saved) ?: return
        choiceStore.setChosen(id, kind, ids)
        monitor?.recount()
    }

    /**
     * Refreshes the catalogue once per visit to the page, so groups added at the source show in
     * the choice screens. Offline, they show what's stored.
     */
    fun refreshCatalogueOnce() {
        if (catalogueRefreshed) return
        catalogueRefreshed = true
        _connection.value?.let { refreshCatalogue(it, onlyIfEmpty = false) }
    }

    fun autoSyncInterval(): String =
        _connection.value?.let { autoSync.interval(it.id) } ?: AUTO_SYNC_OFF

    fun setAutoSyncInterval(interval: String) {
        val id = _connection.value?.id ?: return
        autoSync.set(id, interval, platform.needsNetwork)
    }

    /** Its folder's name in the Library folder, such as "kurage". Settings → Library moves it. */
    fun folderName(): String? {
        val connection = _connection.value ?: return null
        val folder = ConnectionFolders.folderFor(getApplication(), connection, platform.name)
        return LibraryFolderStore(getApplication()).nameInLibrary(folder.path)
    }

    /**
     * Signs the connection out (spec "Running connections"). The page shows it at once; the
     * clearing goes on in the background, and must finish even if Settings closes. The player
     * isn't touched: playback, the queue and book progress stay (D10).
     */
    fun signOut() {
        val connection = _connection.value ?: return
        _connection.value = null
        watch(null)
        val app = getApplication<Application>()
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    ConnectionSignOut(app, connection).run(SyncStates.of(connection.id)) {
                        SyncService.stop(app, connection.id)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The catalogue stays, and the next sign-in's refresh replaces it.
                    Log.w(TAG, "Couldn't finish signing out of ${connection.name}", e)
                }
            }
        }
    }

    override fun onCleared() {
        monitorScope?.cancel()
        super.onCleared()
    }

    // The monitor follows the connection; its state feeds uiState.
    private fun watch(connection: Connection?) {
        monitorState?.let(_uiState::removeSource)
        monitorScope?.cancel()
        choiceLists.clear()
        monitor = null
        monitorScope = null
        monitorState = null
        if (connection == null) {
            _uiState.value = ConnectionUiState()
            return
        }
        // A child of viewModelScope, so the monitor goes when the connection changes or the
        // view model is cleared.
        val parent = viewModelScope.coroutineContext
        val scope = CoroutineScope(parent + SupervisorJob(parent[Job]))
        val watching = ConnectionMonitor(getApplication(), connection, scope)
        val state = watching.state.asLiveData()
        _uiState.addSource(state) { _uiState.value = it }
        monitor = watching
        monitorScope = scope
        monitorState = state
    }

    private fun refreshCatalogue(connection: Connection, onlyIfEmpty: Boolean) {
        viewModelScope.launch {
            try {
                if (onlyIfEmpty && !catalogue.isEmpty(connection.id)) return@launch
                // The same sign-in, user and all, as the fetch was made with (T4).
                val refreshed = catalogue.refresh(connection.id, platform.source(connection)) {
                    Platforms.connection(connection.id) == connection
                }
                if (refreshed) monitor?.recount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Room and bad server data throw too, not only network errors.
                Log.w(TAG, "Couldn't refresh the catalogue", e)
            }
        }
    }
}
