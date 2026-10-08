package com.jpd.hz.sync

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.jpd.hz.adapter.AdapterFolder
import com.jpd.hz.adapter.AdapterFolderStore
import com.jpd.hz.adapter.FirstLibrary
import com.jpd.hz.adapter.FolderMove
import com.jpd.hz.adapter.FolderMoves
import com.jpd.hz.adapter.PendingMove
import com.jpd.hz.adapter.RenameResult
import com.jpd.hz.adapter.hasFiles
import com.jpd.hz.db.SyncDatabase
import com.jpd.hz.library.LibraryFolderStore
import com.jpd.hz.model.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "FolderSetup"
private const val PREFS = "settings"
// A change begun and not yet finished (FolderMoves.encode); see [FolderSetup.perform].
private const val PENDING_MOVE = "library_move"
// Where Jellyfin synced before T2; settled into adapter_folders, then dropped.
private const val LEGACY_SYNC_DIRECTORY = "sync_directory"
// The player's private copies of artist photos and playlist covers, before T3.
private val PRIVATE_ART_FOLDERS = listOf("artist_images", "playlist_images")

/** What a change of Library folder would do, worked out before it's confirmed (A4). */
sealed class LibraryChangePlan {
    object Same : LibraryChangePlan()

    /** The folder can't be listed. */
    object Unreadable : LibraryChangePlan()

    /** It's Jellyfin's folder, or inside it. */
    object Refused : LibraryChangePlan()

    /** Jellyfin's folder is gone from where it was, and isn't in the new folder either. */
    object NotFound : LibraryChangePlan()

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
    /** Saved. [movedTo] is where Jellyfin's files went, when hz moved them. */
    data class Changed(val movedTo: String?) : LibraryChange()

    object Unchanged : LibraryChange()
    object Unreadable : LibraryChange()
    object Refused : LibraryChange()
    object NotFound : LibraryChange()

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
 * Sets the Library folder: once on the first launch after T3, and whenever the Library screen
 * changes it. Both can move Jellyfin's folder, so both hold [lock], which a sync holds for its
 * whole run: a folder never moves under a running sync, whose cleanup would then delete it.
 * Jellyfin's folder and its records are saved relative to the Library folder and to that folder
 * (A1), so a move or a re-point rewrites no record.
 */
class FolderSetup internal constructor(
    private val context: Context,
    private val database: SyncDatabase
) {

    constructor(context: Context) : this(
        context.applicationContext,
        SyncDatabase.getInstance(context.applicationContext)
    )

    private val libraryFolders = LibraryFolderStore(context)
    private val adapterFolders = AdapterFolderStore(context)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The first launch after T3 (A6): settles the Library folder from where Jellyfin synced,
     * moving a hand-picked folder's content into `<it>/<server name>`, and makes the records
     * relative. First it finishes any change that was cut short. True once Jellyfin's folder is
     * where it belongs, so a sync may run; false when a move failed or is unfinished. The caller
     * holds [lock].
     */
    suspend fun settle(config: ServerConfig?): Boolean = withContext(Dispatchers.IO) {
        pendingMove()?.let { return@withContext perform(it) == MoveOutcome.DONE }
        if (libraryFolders.saved() != null) return@withContext true
        // The player reads photos and covers from the Library folder now (approved 2026-10-08).
        PRIVATE_ART_FOLDERS.forEach { File(context.filesDir, it).deleteRecursively() }
        val publicDefault = libraryFolders.publicDefault()
        if (config == null) {
            // Signed out: an install that synced before settles at its next sign-in.
            val syncedBefore = adapterFolders.all().isNotEmpty() || legacyFolder() != null ||
                database.syncDao().trackCount() > 0
            if (!syncedBefore) publicDefault?.let { finishSettling(it.path) }
            return@withContext true
        }
        val folder = SyncEngine.getSyncDirectory(context, config)
        val defaults = listOfNotNull(publicDefault?.path, libraryFolders.appDefault().path)
        val first =
            FolderMoves.firstLibrary(folder.path, defaults, config.serverName, PLATFORM, ::hasFiles)
        val move = when (first) {
            // App storage, used only without all-files access, isn't saved as the library, and
            // the folder keeps its full path, which a later library can't change.
            is FirstLibrary.Parent -> PendingMove(
                library = first.library.takeIf { publicDefault != null },
                folders = if (publicDefault != null) {
                    listOf(relativeFolder(config, folder.path, first.library))
                } else {
                    emptyList()
                },
                renames = emptyList(),
                recordsFrom = folder.path
            )
            is FirstLibrary.MoveInto -> PendingMove(
                library = first.library,
                folders = listOf(relativeFolder(config, first.target, first.library)),
                renames = FolderMoves.ADAPTER_CONTENT.map {
                    File(folder, it).path to File(first.target, it).path
                },
                recordsFrom = folder.path
            )
        }
        perform(move) == MoveOutcome.DONE
    }

    /** At app open, unless a sync holds the lock: that sync settles the folder first. */
    suspend fun settleAtLaunch(config: ServerConfig?) {
        if (!lock.tryLock()) return
        try {
            settle(config)
        } finally {
            lock.unlock()
        }
    }

    /**
     * What changing the Library folder to [path] would do (A4), for the Library screen to
     * confirm: for each saved adapter folder, refuse, keep it, move it, or find where it was
     * moved by hand. Only [serverId]'s folder is looked for; another server's is kept as saved.
     */
    suspend fun plan(path: String, serverId: String?): LibraryChangePlan =
        withContext(Dispatchers.IO) {
        val newLibrary = File(path).absoluteFile
        if (!newLibrary.isDirectory || newLibrary.list() == null) {
            return@withContext LibraryChangePlan.Unreadable
        }
        val oldLibrary = libraryFolders.folder().absoluteFile
        if (newLibrary.path.equals(oldLibrary.path, ignoreCase = true)) {
            return@withContext LibraryChangePlan.Same
        }
        val saved = adapterFolders.all()
        val planned = ArrayList<PlannedFolder>()
        for (folder in saved) {
            val old = File(FolderMoves.resolve(folder.path, oldLibrary.path))
            val next = when {
                old.isDirectory -> plannedFor(folder, old, newLibrary, saved, planned)
                    ?: return@withContext LibraryChangePlan.Refused
                folder.serverId == serverId -> findMoved(folder, newLibrary)
                    ?: return@withContext LibraryChangePlan.NotFound
                else -> PlannedFolder(folder, old.path, rename = false)
            }
            planned.add(next)
        }
        LibraryChangePlan.Ready(newLibrary.path, planned)
    }

    /**
     * Changes the Library folder to [path] (A4). Adapter folders inside it are only saved again;
     * others are renamed into it; one moved by hand is found by its files. A failed rename changes
     * nothing. Busy while a sync runs.
     */
    suspend fun changeLibrary(path: String, serverId: String?): LibraryChange {
        if (!lock.tryLock()) return LibraryChange.Busy
        try {
            return withContext(Dispatchers.IO) { change(path, serverId) }
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
        // Any folder already at the target is taken: a rename never replaces one, even empty.
        val move = FolderMoves.moveFor(old.path, newLibrary.path, PLATFORM) { candidate ->
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
    // folder under it holding most of its recorded files (A4). With no records, nothing has
    // synced yet, so the saved relative path is simply kept.
    private suspend fun findMoved(folder: AdapterFolder, newLibrary: File): PlannedFolder? {
        val dao = database.syncDao()
        // The catalogue names the signed-in server's records; without one, every record counts.
        val sizes = dao.serverRecordSizes().ifEmpty { dao.allRecordSizes() }
        val records = sizes.associate { it.localPath to it.fileSize }
        // A folder saved before T3 has a full path; its name stands in for a relative one.
        val relative = if (folder.path.startsWith('/')) File(folder.path).name else folder.path
        val atSavedPath = File(newLibrary, relative)
        if (records.isEmpty()) return PlannedFolder(folder, atSavedPath.path, rename = false)
        if (FolderMoves.holdsRecords(atSavedPath, records)) {
            return PlannedFolder(folder, atSavedPath.path, rename = false)
        }
        val found = FolderMoves.findAdapterFolder(newLibrary, records) ?: return null
        return PlannedFolder(folder, found.path, rename = false, found = true)
    }

    private suspend fun change(path: String, serverId: String?): LibraryChange {
        pendingMove()?.let {
            if (perform(it) != MoveOutcome.DONE) return LibraryChange.MoveUnfinished
        }
        // Until the first settle has moved Jellyfin's files and made its records relative, a
        // change would skip that step (A6).
        if (libraryFolders.saved() == null) return LibraryChange.MoveUnfinished
        val plan = when (val planned = plan(path, serverId)) {
            LibraryChangePlan.Same -> return LibraryChange.Unchanged
            LibraryChangePlan.Unreadable -> return LibraryChange.Unreadable
            LibraryChangePlan.Refused -> return LibraryChange.Refused
            LibraryChangePlan.NotFound -> return LibraryChange.NotFound
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
            MoveOutcome.DONE ->
                LibraryChange.Changed(plan.folders.firstOrNull { it.rename }?.target)
            MoveOutcome.NOTHING_MOVED -> LibraryChange.MoveFailed
            MoveOutcome.UNFINISHED -> LibraryChange.MoveUnfinished
        }
    }

    /**
     * Every step of a change, in an order that survives being cut short:
     * 1. Room opens first, so an upgrade's migration fails, if it does, before any rename.
     * 2. The change is saved before the first rename, so if hz stops partway, the next settle
     *    finishes it (a rename whose source is gone is skipped).
     * 3. The renames; one that fails puts the others from this run back.
     * 4. Records saved with full paths before T3 become relative, once (A6).
     * 5. The folders, then the Library folder, saved with commit().
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
                Log.w(TAG, "Couldn't move Jellyfin's files into ${move.library}; nothing changed")
                prefs.edit().remove(PENDING_MOVE).commit()
                return@withContext MoveOutcome.NOTHING_MOVED
            }
            move.recordsFrom?.let { makeRecordsRelative(it) }
            move.folders.forEach(adapterFolders::save)
            finishSettling(move.library)
            MoveOutcome.DONE
        }

    // Full paths under [folder] lose its part; any other full path goes (its file isn't the
    // adapter's). Running it again changes nothing, as relative paths don't start with "/".
    private suspend fun makeRecordsRelative(folder: String) {
        val prefix = "${folder.trimEnd('/')}/"
        val dao = database.syncDao()
        database.withTransaction {
            dao.makeTrackPathsRelative(prefix)
            dao.deleteFullPathTracks()
            dao.makeArtworkPathsRelative(prefix)
        }
    }

    // The Library folder is saved last, when there is one to save; the change and the old
    // setting go with it.
    private fun finishSettling(library: String?) {
        library?.let(libraryFolders::save)
        prefs.edit().remove(PENDING_MOVE).remove(LEGACY_SYNC_DIRECTORY).commit()
    }

    private fun relativeFolder(config: ServerConfig, folder: String, library: String) =
        AdapterFolder(ADAPTER, config.serverId, FolderMoves.relativeOf(folder, library) ?: folder)

    private fun pendingMove(): PendingMove? =
        FolderMoves.decode(prefs.getString(PENDING_MOVE, null))

    private fun legacyFolder(): String? =
        prefs.getString(LEGACY_SYNC_DIRECTORY, null)?.takeIf { it.isNotBlank() }

    companion object {
        /** Held by every sync for its whole run, and by every move of Jellyfin's folder. */
        val lock = Mutex()
    }
}
