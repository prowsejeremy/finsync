package com.jpd.finsync.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.finsync.library.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "LibraryViewModel"

/** Home's library state. Builds the catalogue on first open when it's empty, without a sync. */
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val library = LibraryRepository(app)
    private val refreshStatus = MutableStateFlow(RefreshStatus.IDLE)

    val homeState: LiveData<HomeLibraryState> = combine(
        library.isCatalogueEmpty(),
        library.albumCount(),
        refreshStatus
    ) { empty, count, status -> homeLibraryStateOf(empty, count, status) }.asLiveData()

    init {
        refreshIfEmpty()
    }

    fun retry() = refreshIfEmpty()

    private fun refreshIfEmpty() {
        if (refreshStatus.value == RefreshStatus.RUNNING) return
        // Set before launching, so a second tap can't start a second refresh.
        refreshStatus.value = RefreshStatus.RUNNING
        viewModelScope.launch {
            // Room and bad server data throw too, not only network errors.
            val succeeded = try {
                !library.isCatalogueEmpty().first() || library.refreshCatalogue()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't build catalogue", e)
                false
            }
            refreshStatus.value = if (succeeded) RefreshStatus.DONE else RefreshStatus.FAILED
        }
    }
}
