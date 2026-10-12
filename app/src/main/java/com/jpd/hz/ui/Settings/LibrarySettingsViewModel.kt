package com.jpd.hz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jpd.hz.library.LibraryFolderStore
import com.jpd.hz.library.LibraryRepository
import com.jpd.hz.library.scan.LibraryScanner
import com.jpd.hz.library.scan.ScanState
import com.jpd.hz.adapter.folders.FolderSetup
import com.jpd.hz.adapter.folders.LibraryChange
import com.jpd.hz.adapter.folders.LibraryChangePlan
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/** Settings → Library (spec "Settings → Library") and the Settings row's summary. */
class LibrarySettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val folders = LibraryFolderStore(app)
    private val setup = FolderSetup(app)
    private val scanner = LibraryScanner.get(app)

    val scanState: LiveData<ScanState> = scanner.state.asLiveData()

    val songCount: LiveData<Int> = LibraryRepository(app).songCount().asLiveData()

    private val _folder = MutableLiveData(folders.displayPath())
    /** The Library folder as screens name it, such as "Media/hz". */
    val folder: LiveData<String> = _folder

    private val _changes = MutableSharedFlow<LibraryChange>(extraBufferCapacity = 1)
    /** How each change went, for the screen to report. */
    val changes: SharedFlow<LibraryChange> = _changes

    /** Where the folder picker opens. */
    fun folderPath(): String = folders.folder().path

    fun rescan() = scanner.rescan()

    /** What choosing [path] would do, so the screen can confirm a move first. */
    suspend fun plan(path: String): LibraryChangePlan = setup.plan(path)

    /** Changes the Library folder, then rescans: "The Library folder changes" (spec). */
    fun change(path: String) {
        viewModelScope.launch {
            val result = setup.changeLibrary(path)
            if (result is LibraryChange.Changed) {
                _folder.value = folders.displayPath()
                scanner.requestScan()
            }
            _changes.emit(result)
        }
    }

    /** [path] as screens name it. */
    fun displayPathOf(path: String): String = folders.displayPathOf(path)
}
