package com.jpd.hz.adapter.run

import android.content.Context
import android.util.Log
import com.jpd.hz.adapter.CatalogueResult
import com.jpd.hz.adapter.ChoiceKind
import com.jpd.hz.adapter.Connection
import com.jpd.hz.adapter.Platform
import com.jpd.hz.adapter.Platforms
import com.jpd.hz.adapter.Source
import com.jpd.hz.adapter.SourceCatalogue
import com.jpd.hz.adapter.SourceItem
import com.jpd.hz.adapter.choices.ChoiceRules
import com.jpd.hz.adapter.choices.ChoiceStore
import com.jpd.hz.adapter.db.RecordDao
import com.jpd.hz.adapter.db.SyncDatabase
import com.jpd.hz.adapter.db.SyncedFile
import com.jpd.hz.adapter.files.AdapterFiles
import com.jpd.hz.adapter.files.FileTagger
import com.jpd.hz.adapter.files.TagLibTagger
import com.jpd.hz.adapter.files.TagResult
import com.jpd.hz.adapter.files.cleanUpAdapterFolder
import com.jpd.hz.adapter.folders.ConnectionFolders
import com.jpd.hz.adapter.folders.FolderSetup
import com.jpd.hz.adapter.folders.syncFolderProblemOf
import com.jpd.hz.library.LibraryFolderStore
import com.jpd.hz.tags.TagFingerprint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val TAG = "SyncRun"
// Each file's tries in one run; the next sync tries again (3b spec).
private const val ATTEMPTS = 2
private const val COPY_BUFFER_BYTES = 64 * 1024
private const val FETCHING = "Fetching library..."
// A change of Library folder failed or is unfinished (FolderSetup), so nothing may sync.
private const val NOT_SETTLED =
    "Couldn't move the adapters' files into the Library folder. Nothing was synced; hz tries " +
        "again next time."

/**
 * One connection's sync (spec "The sync run"), for every platform. It owns everything that
 * happens on the phone: the folder, `.part` → tag → rename, records, the keep set and cleanup.
 * The connection's [Source] owns what's there and how to read it.
 *
 * Runs take turns under [FolderSetup.lock]; one waiting for it shows Waiting. Once it holds the
 * lock, it reads the connection again and runs nothing if it has signed out (T4).
 */
class SyncRun internal constructor(
    private val connectionId: String,
    private val database: SyncDatabase,
    private val choices: ChoiceStore,
    // The edge where TagLib comes in. Unit tests never reach it (see FileTagger).
    private val tagger: FileTagger,
    // The installed platforms, the connection's folder, the Library folder and the settle: the
    // Android edges, which unit tests pass.
    private val platforms: () -> List<Platform>,
    private val folderFor: (Connection, Platform) -> File,
    private val library: () -> File,
    private val settle: suspend () -> Boolean
) {

    constructor(context: Context, connectionId: String) : this(
        connectionId,
        SyncDatabase.getInstance(context.applicationContext),
        ChoiceStore(context),
        TagLibTagger,
        { Platforms.all },
        { connection, platform ->
            ConnectionFolders.folderFor(context.applicationContext, connection, platform.name)
        },
        { LibraryFolderStore(context.applicationContext).folder() },
        { FolderSetup(context.applicationContext).settle() }
    )

    private val records: RecordDao = database.recordDao()
    private val catalogue = Catalogue(database.catalogueDao())
    private var onProgress: ((SyncState) -> Unit)? = null

    suspend fun run(onProgress: ((SyncState) -> Unit)? = null) {
        this.onProgress = onProgress
        if (!FolderSetup.lock.tryLock()) {
            emit(SyncState(isRunning = true, waiting = true))
            FolderSetup.lock.lock()
        }
        try {
            runLocked()
        } finally {
            FolderSetup.lock.unlock()
        }
    }

    private suspend fun runLocked() {
        val platformKey = Connection.platformOf(connectionId)
        val platform = platforms().firstOrNull { it.key == platformKey }
        // Signed out (or the platform gone) since this run was asked for: sign-out cleared the
        // catalogue under this lock, and this run's write mustn't bring it back.
        val connection = platform?.connections()?.firstOrNull { it.id == connectionId }
        if (platform == null || connection == null) {
            emit(SyncState())
            return
        }
        val chosen = choices.chosen(connectionId, platform.choiceKinds)
        if (ChoiceRules.nothingChosen(chosen.values)) {
            // Not an error: the run doesn't start, and the screens say why (spec H4).
            emit(SyncState(nothingChosen = true))
            return
        }
        if (!settled()) {
            emit(SyncState(errorMessage = NOT_SETTLED))
            return
        }
        emit(SyncState(isRunning = true, currentTrack = FETCHING))
        val source = platform.source(connection)
        val fetched = source.catalogue()
        if (fetched is CatalogueResult.Failure) {
            emit(SyncState(errorMessage = fetched.message))
            return
        }
        val fresh = (fetched as CatalogueResult.Success).catalogue
        syncFrom(connection, platform, source, fresh, chosen)
    }

    private suspend fun settled(): Boolean = try {
        settle()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't settle the Library folder", e)
        false
    }

    private suspend fun syncFrom(
        connection: Connection,
        platform: Platform,
        source: Source,
        fresh: SourceCatalogue,
        chosen: Map<ChoiceKind, Set<String>>
    ) {
        val stored = writeCatalogue(fresh)
        // A failed write plans from this fetch alone, so cleanup waits for the next sync (below).
        val groups = (stored ?: mergeFailedParts(fresh, StoredCatalogue.EMPTY)).groups
        val plan = planOf(groups, fresh.items, chosen)
        Log.i(TAG, "${connection.name}: ${plan.items.size} items to sync")
        emit(SyncStates.of(connectionId).value.copy(totalItems = plan.items.size))

        val folder = folderFor(connection, platform)
        val problem = syncFolderProblemOf(
            name = connection.name,
            folder = folder.absolutePath,
            exists = folder.isDirectory,
            hasRecords = records.count(connectionId) > 0,
            library = library().absolutePath
        )
        if (problem != null) {
            emit(SyncState(errorMessage = problem))
            return
        }
        folder.mkdirs()
        if (!folder.canWrite()) {
            emit(SyncState(errorMessage = "Cannot write to ${folder.absolutePath}"))
            return
        }

        val extras = source.extras(plan)
        val keep = keepSetOf(plan, extras).mapTo(HashSet()) { File(folder, it).absolutePath }

        val outcome = syncItems(source, folder, plan.items)
        if (!currentCoroutineContext().isActive) return

        // Without the stored catalogue, the plan may miss a failed part's files: no cleanup.
        if (stored != null && mayCleanUp(fresh, groups, chosen, plan)) {
            // Shared adapter code (D12): only ever inside this connection's folder.
            withContext(Dispatchers.IO) { cleanUpAdapterFolder(folder, keep) }
            dropRecordsOfMissingFiles(folder)
        } else {
            Log.i(TAG, "${connection.name}: cleanup waits for a full fetch")
        }
        // After cleanup, so covers and photos follow the albums that stayed.
        if (stored != null) Extras.run(folder, source, plan, extras)

        emit(
            SyncState(
                totalItems = plan.items.size,
                downloadedItems = plan.items.size,
                isRunning = false,
                bytesDownloaded = outcome.bytes,
                syncComplete = true,
                failedItems = failedChoiceCount(fresh, groups, chosen) + outcome.failed,
                untaggedFiles = outcome.untagged
            )
        )
    }

    // Before any download, so a cancelled sync still leaves the catalogue current. A failure
    // mustn't stop the sync itself. Returns what was stored, or null.
    private suspend fun writeCatalogue(fresh: SourceCatalogue): StoredCatalogue? = try {
        catalogue.write(connectionId, fresh)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't write the catalogue", e)
        null
    }

    /** What the items step did, for the run's last state. */
    private class ItemsOutcome(val bytes: Long, val failed: Int, val untagged: Int)

    private suspend fun syncItems(
        source: Source,
        folder: File,
        items: List<SourceItem>
    ): ItemsOutcome {
        var bytes = 0L
        var failed = 0
        var untagged = 0
        val paths = HashSet<String>()
        items.forEachIndexed { index, item ->
            if (!currentCoroutineContext().isActive) return@forEachIndexed
            // Shared storage ignores case, so two paths that differ only in case are one file.
            if (!isSafePath(item.path) || !paths.add(item.path.lowercase())) {
                Log.w(TAG, "Skipped ${item.id}: its path ${item.path} is unsafe or taken")
                failed++
            } else {
                when (val result = syncItem(source, folder, item, index)) {
                    ItemResult.Failed -> failed++
                    is ItemResult.Synced -> {
                        bytes += result.bytes
                        if (result.untagged) untagged++
                    }
                }
            }
            emit(
                SyncStates.of(connectionId).value.copy(
                    downloadedItems = index + 1,
                    bytesDownloaded = bytes
                )
            )
        }
        return ItemsOutcome(bytes, failed, untagged)
    }

    private sealed interface ItemResult {
        object Failed : ItemResult

        class Synced(val bytes: Long, val untagged: Boolean) : ItemResult
    }

    // Downloads what's missing or changed, and re-tags what's on disk with other fields (spec
    // "The sync run", step 6). Two attempts; after a failed tag, the retry keeps its fresh
    // download untagged, as the first was deleted (a failed write can leave a file half-written).
    private suspend fun syncItem(
        source: Source,
        folder: File,
        item: SourceItem,
        index: Int
    ): ItemResult {
        val file = File(folder, item.path)
        var tag = true
        repeat(ATTEMPTS) {
            if (!needsDownload(file, item)) {
                return ItemResult.Synced(0L, untagged = !retagIfChanged(file, item))
            }
            emit(
                SyncStates.of(connectionId).value.copy(
                    currentTrack = item.label,
                    downloadedItems = index
                )
            )
            file.parentFile?.mkdirs()
            when (val attempt = downloadAndTag(source, item, file, tag)) {
                Attempt.Failed -> Unit
                Attempt.TagFailed -> {
                    Log.w(TAG, "Couldn't tag ${file.name}; downloading it untagged")
                    tag = false
                }
                is Attempt.Done -> return ItemResult.Synced(attempt.bytes, attempt.untagged)
            }
        }
        return ItemResult.Failed
    }

    private suspend fun needsDownload(file: File, item: SourceItem): Boolean {
        if (!file.exists() || file.length() == 0L) return true
        val record = records.get(connectionId, item.id)
        if (record == null) {
            // On disk with no record (a new install, a rebuilt database): re-linked, not
            // downloaded. It has no fingerprint, so the re-tag check below tags it.
            records.upsert(
                SyncedFile(connectionId, item.id, item.path, item.version, file.length())
            )
            return false
        }
        if (record.version != null && item.version != null && record.version != item.version) {
            return true
        }
        return file.length() != record.fileSize
    }

    /** How one download attempt went. */
    private sealed interface Attempt {
        /** Nothing landed; the caller tries once more. */
        object Failed : Attempt

        /** TagLib's write failed, so the download was deleted. The retry doesn't tag. */
        object TagFailed : Attempt

        class Done(val bytes: Long, val untagged: Boolean) : Attempt
    }

    // To "<file>.part", tagged there unless [tag] is false or the item has no fields, then
    // renamed into place, so the file is never seen half-written or half-tagged. The record
    // holds the size after tagging, so needsDownload doesn't fetch a tagged file again.
    private suspend fun downloadAndTag(
        source: Source,
        item: SourceItem,
        file: File,
        tag: Boolean
    ): Attempt {
        val part = AdapterFiles.partOf(file)
        try {
            val written = source.open(item).use { writeStreamToFile(it, part) }
            if (written <= 0L) {
                part.delete()
                return Attempt.Failed
            }
            // The file and its record land together. A stop between them would leave the new
            // size unrecorded, and the next sync would download the file again.
            return withContext(NonCancellable) { finishDownload(item, part, file, tag, written) }
        } catch (e: CancellationException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't download ${item.label}", e)
            part.delete()
            return Attempt.Failed
        }
    }

    private suspend fun finishDownload(
        item: SourceItem,
        part: File,
        file: File,
        tag: Boolean,
        written: Long
    ): Attempt {
        val fields = item.fields
        val toWrite = if (tag && fields != null) fields else emptyMap()
        val result = withContext(Dispatchers.IO) {
            AdapterFiles.finishDownload(part, file, toWrite, tagger)
        }
        if (result == TagResult.FAILED) {
            return if (toWrite.isNotEmpty()) Attempt.TagFailed else Attempt.Failed
        }
        if (result == TagResult.UNREADABLE) Log.w(TAG, "${file.name} keeps its own tags")
        val tagged = toWrite.isNotEmpty() && result == TagResult.TAGGED
        records.upsert(
            SyncedFile(
                connectionId = connectionId,
                itemId = item.id,
                path = item.path,
                version = item.version,
                fileSize = file.length(),
                tagFingerprint = if (tagged) TagFingerprint.of(toWrite) else null
            )
        )
        return Attempt.Done(written, untagged = fields != null && !tagged)
    }

    // A file already on the device whose fields changed at the source, or that was never tagged.
    // False when it keeps its own tags; its fingerprint stays as it was, so the next sync tries
    // again. An item with no fields is never re-tagged.
    private suspend fun retagIfChanged(file: File, item: SourceItem): Boolean {
        val fields = item.fields ?: return true
        val record = records.get(connectionId, item.id) ?: return true
        val fingerprint = TagFingerprint.of(fields)
        if (record.tagFingerprint == fingerprint) return true
        return withContext(NonCancellable) {
            // As in downloadAndTag: the renamed file and its new size are recorded together.
            val result = try {
                withContext(Dispatchers.IO) { AdapterFiles.retag(file, fields, tagger) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't re-tag ${file.name}", e)
                TagResult.FAILED
            }
            if (result != TagResult.TAGGED) {
                Log.w(TAG, "${file.name} keeps its own tags ($result)")
                return@withContext false
            }
            records.upsert(
                record.copy(
                    path = item.path,
                    fileSize = file.length(),
                    tagFingerprint = fingerprint
                )
            )
            true
        }
    }

    // Drops this connection's records whose files aren't in its folder. Another connection's
    // records name files in its own folder, so they're kept (T4).
    private suspend fun dropRecordsOfMissingFiles(folder: File) {
        for (record in records.paths(connectionId)) {
            if (!File(folder, record.path).exists()) records.deleteByPath(connectionId, record.path)
        }
    }

    private suspend fun writeStreamToFile(stream: InputStream, dest: File): Long =
        withContext(Dispatchers.IO) {
            try {
                FileOutputStream(dest).use { out -> stream.copyTo(out, COPY_BUFFER_BYTES) }
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't write ${dest.name}", e)
                dest.delete()
                0L
            }
        }

    private fun emit(state: SyncState) {
        SyncStates.set(connectionId, state)
        onProgress?.invoke(state)
    }
}
