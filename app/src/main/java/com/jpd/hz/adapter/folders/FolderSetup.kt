package com.jpd.hz.adapter.folders

import android.content.Context
import android.util.Log
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.adapter.db.SyncDatabase
import com.jpd.hz.library.LibraryFolderStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "FolderSetup"
private const val PREFS = "settings"
// A change begun and not yet finished (FolderMoves.encode); see [FolderSetup.perform].
private const val PENDING_MOVE = "library_move"

/** What a change of Library folder would do, worked out before it's confirmed (A4). */
sealed class LibraryChangePlan {
    object Same : LibraryChangePlan()

    /** The folder can't be listed. */
    object Unreadable : LibraryChangePlan()

    /** It's a connection's folder, or inside one. */
    object Refused : LibraryChangePlan()

    /** A signed-in connection's folder is gone from where it was, and isn't in the new folder. */
    data class NotFound(val folder: AdapterFolder) : LibraryChangePlan()

    data class Ready(val newLibrary: String, val folders: List<PlannedFolder>) :
        LibraryChangePlan() {
        /** Files move, so the Library screen asks first. */
        val movesFiles: Boolean get() = folders.any { it.rename }

        /** A folder found by searching, which the Library screen asks the user to confirm. */
        val found: PlannedFolder? get() = folders.firstOrNull { it.found }
    }
}

/**
 * Where an adapter folder will be, as a full path. [rename] when hz moves it there; otherwise it's
 * already there and is only saved again. [found] when it was found by searching the new folder.
 */
data class PlannedFolder(
    val folder: AdapterFolder,
    val target: String,
    val rename: Boolean,
    val found: Boolean = false
)

/** How a change of Library folder went. */
sealed class LibraryChange {
    /** Saved. [moved] are the folders hz moved, and where they went. */
    data class Changed(val moved: List<PlannedFolder>) : LibraryChange()

    object Unchanged : LibraryChange()
    object Unreadable : LibraryChange()
    object Refused : LibraryChange()
    data class NotFound(val folder: AdapterFolder) : LibraryChange()

    /** A sync is running; nothing changed. */
    object Busy : LibraryChange()

    /** A rename failed and every folder went back: nothing changed. */
    object MoveFailed : LibraryChange()

    /** Some folders moved and one couldn't; the next settle finishes the move. */
    object MoveUnfinished : LibraryChange()
}

/** How [FolderSetup.perform] went. */
private enum class MoveOutcome {
    DONE,

    /** Nothing moved, now or before: the change is dropped. */
    NOTHING_MOVED,

    /** Some folders moved; the saved change is finished by the next settle. */
    UNFINISHED
}

/**
 * Sets the Library folder: on a fresh install, and whenever the Library screen changes it. A
 * change can move connections' folders, so it holds [lock], which every sync holds for its whole
 * run: a folder never moves under a running sync, whose cleanup would then delete it. Folders and
 * records are saved relative to the Library folder and to their folder (A1), so a move or a
 * re-point rewrites no record.
 */
class FolderSetup internal constructor(
    private val context: Context,
    private val database: SyncDatabase,
    // The signed-in connections. Unit tests pass their own.
    private val signedIn: () -> Set<String>
) {

    constructor(context: Context) : this(
        context.applicationContext,
        SyncDatabase.getInstance(context.applicationContext),
        { Platforms.connections().mapTo(HashSet()) { it.id } }
    )

    private val libraryFolders = LibraryFolderStore(context)
    private val adapterFolders = AdapterFolderStore(context)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Finishes a change of Library folder that was cut short, and on a fresh install saves the
     * public `Media/hz` as the Library folder (spec H5). True once nothing is pending, so a sync
     * may run. The caller holds [lock].
     */
    suspend fun settle(): Boolean = withContext(Dispatchers.IO) {
        pendingMove()?.let { return@withContext perform(it) == MoveOutcome.DONE }
        if (libraryFolders.saved() == null) libraryFolders.publicDefault()?.let { finish(it.path) }
        true
    }

    /** At app open, unless a sync holds the lock: that sync settles the folder first. */
    suspend fun settleAtLaunch() {
        if (!lock.tryLock()) return
        try {
            settle()
        } finally {
            lock.unlock()
        }
    }

    /**
     * What changing the Library folder to [path] would do (A4), for the Library screen to
     * confirm: for each saved connection folder, refuse, keep it, move it, or find where it was
     * moved by hand, from that connection's own records. A signed-in connection's folder that
     * can't be found stops the change; a signed-out one's stays as saved, and its next sync's
     * folder guard stops it from downloading everything again.
     */
    suspend fun plan(path: String): LibraryChangePlan = withContext(Dispatchers.IO) {
        val newLibrary = File(path).absoluteFile
        if (!newLibrary.isDirectory || newLibrary.list() == null) {
            return@withContext LibraryChangePlan.Unreadable
        }
        val oldLibrary = libraryFolders.folder().absoluteFile
        if (newLibrary.path.equals(oldLibrary.path, ignoreCase = true)) {
            return@withContext LibraryChangePlan.Same
        }
        val saved = adapterFolders.all()
        val signedInIds = signedIn()
        val planned = ArrayList<PlannedFolder>()
        for (folder in saved) {
            val old = File(FolderMoves.resolve(folder.path, oldLibrary.path))
            val next = when {
                old.isDirectory -> plannedFor(folder, old, newLibrary, saved, planned)
                    ?: return@withContext LibraryChangePlan.Refused
                else -> findMoved(folder, newLibrary, planned)
                    ?: if (connectionIdOf(folder) in signedInIds) {
                        return@withContext LibraryChangePlan.NotFound(folder)
                    } else {
                        PlannedFolder(folder, old.path, rename = false)
                    }
            }
            planned.add(next)
        }
        LibraryChangePlan.Ready(newLibrary.path, planned)
    }

    /**
     * Changes the Library folder to [path] (A4). Connection folders inside it are only saved
     * again; others are renamed into it; one moved by hand is found by its files. A failed rename
     * changes nothing. Busy while a sync runs.
     */
    suspend fun changeLibrary(path: String): LibraryChange {
        if (!lock.tryLock()) return LibraryChange.Busy
        try {
            return withContext(Dispatchers.IO) { change(path) }
        } finally {
            lock.unlock()
        }
    }

    // A folder still where it was: kept, refused (null) or moved, by FolderMoves.moveFor.
    private fun plannedFor(
        folder: AdapterFolder,
        old: File,
        newLibrary: File,
        saved: List<AdapterFolder>,
        planned: List<PlannedFolder>
    ): PlannedFolder? {
        val oldLibrary = libraryFolders.folder().absolutePath
        val platform = Platforms.find(folder.adapter)?.name ?: folder.adapter
        // Any folder already at the target is taken: a rename never replaces one, even empty.
        val move = FolderMoves.moveFor(old.path, newLibrary.path, platform) { candidate ->
            File(candidate).exists() ||
                saved.any { FolderMoves.resolve(it.path, oldLibrary).equals(candidate, true) } ||
                planned.any { it.target.equals(candidate, ignoreCase = true) }
        }
        return when (move) {
            FolderMove.Refuse -> null
            FolderMove.Keep -> PlannedFolder(folder, old.path, rename = false)
            is FolderMove.MoveTo -> PlannedFolder(folder, move.target, rename = true)
        }
    }

    // A folder gone from where it was: at its saved relative path in the new folder, else any
    // folder under it holding most of its connection's recorded files (A4). With no records
    // (nothing synced yet, or a rebuilt database), the saved relative path is kept only where
    // nothing is: a folder with files there may be anyone's, and cleanup would empty it.
    private suspend fun findMoved(
        folder: AdapterFolder,
        newLibrary: File,
        planned: List<PlannedFolder>
    ): PlannedFolder? {
        val sizes = database.recordDao().sizes(connectionIdOf(folder))
        val records = sizes.associate { it.path to it.fileSize }
        val atSavedPath = File(newLibrary, folder.path)
        val taken = { path: String -> planned.any { it.target.equals(path, ignoreCase = true) } }
        if (records.isEmpty()) {
            if (hasFiles(atSavedPath.path) || taken(atSavedPath.path)) return null
            return PlannedFolder(folder, atSavedPath.path, rename = false)
        }
        if (FolderMoves.holdsRecords(atSavedPath, records) && !taken(atSavedPath.path)) {
            return PlannedFolder(folder, atSavedPath.path, rename = false)
        }
        val found = FolderMoves.findAdapterFolder(newLibrary, records) ?: return null
        if (taken(found.path)) return null
        return PlannedFolder(folder, found.path, rename = false, found = true)
    }

    private suspend fun change(path: String): LibraryChange {
        pendingMove()?.let {
            if (perform(it) != MoveOutcome.DONE) return LibraryChange.MoveUnfinished
        }
        val plan = when (val planned = plan(path)) {
            LibraryChangePlan.Same -> return LibraryChange.Unchanged
            LibraryChangePlan.Unreadable -> return LibraryChange.Unreadable
            LibraryChangePlan.Refused -> return LibraryChange.Refused
            is LibraryChangePlan.NotFound -> return LibraryChange.NotFound(planned.folder)
            is LibraryChangePlan.Ready -> planned
        }
        val oldLibrary = libraryFolders.folder().absolutePath
        val move = PendingMove(
            library = plan.newLibrary,
            folders = plan.folders.map {
                val relative = FolderMoves.relativeOf(it.target, plan.newLibrary)
                it.folder.copy(path = relative ?: it.target)
            },
            renames = plan.folders.filter { it.rename }.map {
                FolderMoves.resolve(it.folder.path, oldLibrary) to it.target
            }
        )
        return when (perform(move)) {
            MoveOutcome.DONE -> LibraryChange.Changed(plan.folders.filter { it.rename })
            MoveOutcome.NOTHING_MOVED -> LibraryChange.MoveFailed
            MoveOutcome.UNFINISHED -> LibraryChange.MoveUnfinished
        }
    }

    /**
     * Every step of a change, in an order that survives being cut short:
     * 1. Room opens first, so an upgrade's rebuild happens, if it does, before any rename.
     * 2. The change is saved before the first rename, so if hz stops partway, the next settle
     *    finishes it (a rename whose source is gone is skipped).
     * 3. The renames; one that fails puts the others from this run back.
     * 4. The folders, then the Library folder, saved with commit().
     * Nothing in it can be cancelled.
     */
    private suspend fun perform(move: PendingMove): MoveOutcome =
        withContext(NonCancellable + Dispatchers.IO) {
            database.openHelper.writableDatabase
            if (!prefs.edit().putString(PENDING_MOVE, FolderMoves.encode(move)).commit()) {
                return@withContext MoveOutcome.NOTHING_MOVED
            }
            val renames = move.renames.map { (source, target) -> File(source) to File(target) }
            // A scan running now would see half a move; this tells it to throw its pass away.
            LibraryFolderStore.noteFolderChange()
            val outcome = FolderMoves.renameAll(renames).result
            LibraryFolderStore.noteFolderChange()
            if (outcome != RenameResult.DONE) {
                // Something moved when a source is gone and its target is there.
                if (renames.any { (source, target) -> !source.exists() && target.exists() }) {
                    Log.w(TAG, "A move into ${move.library} is unfinished; it resumes next time")
                    return@withContext MoveOutcome.UNFINISHED
                }
                Log.w(TAG, "Couldn't move the adapters' files into ${move.library}; no change")
                prefs.edit().remove(PENDING_MOVE).commit()
                return@withContext MoveOutcome.NOTHING_MOVED
            }
            move.folders.forEach(adapterFolders::save)
            finish(move.library)
            MoveOutcome.DONE
        }

    // The Library folder is saved last, when there is one to save; the change goes with it.
    private fun finish(library: String?) {
        library?.let(libraryFolders::save)
        prefs.edit().remove(PENDING_MOVE).commit()
    }

    private fun pendingMove(): PendingMove? =
        FolderMoves.decode(prefs.getString(PENDING_MOVE, null))

    private fun connectionIdOf(folder: AdapterFolder) =
        Connection.idOf(folder.adapter, folder.serverId)

    companion object {
        /**
         * Held by every sync for its whole run, by every move of a connection's folder, and by
         * sign-out while it clears a catalogue (T4). Runs take turns under it.
         */
        val lock = Mutex()
    }
}
