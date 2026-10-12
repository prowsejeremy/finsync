package com.jpd.hz.library.scan

import android.content.Context
import android.util.Log
import com.jpd.hz.library.LibraryFiles
import com.jpd.hz.library.LibraryFolderStore
import com.jpd.hz.library.db.FileKind
import com.jpd.hz.library.db.LibraryBook
import com.jpd.hz.library.db.LibraryBookChapter
import com.jpd.hz.library.db.LibraryContents
import com.jpd.hz.library.db.LibraryDatabase
import com.jpd.hz.library.db.LibraryFile
import com.jpd.hz.library.db.LibraryScanDao
import com.jpd.hz.library.db.LibraryTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "LibraryScanner"
// Reads on shared storage go through FUSE: slow per call, but they run well side by side. The
// phone spike read 1,477 files in 29.6 s on one thread and in 2.5 s on eight.
private const val READ_THREADS = 8
// The ID a new file is read under, before identify() gives it its real one.
private const val UNIDENTIFIED = ""

/**
 * Builds the player's library from the Library folder (spec "Scanning"). One scan runs at a
 * time; a request during a scan sets a flag that runs exactly one more pass afterwards. A pass
 * writes every table in one transaction at its end, so an interrupted pass writes nothing, and a
 * folder that can't be listed leaves the library as it was.
 */
class LibraryScanner internal constructor(
    private val dao: LibraryScanDao,
    private val folder: () -> File,
    private val scannedFolder: () -> String?,
    private val saveScannedFolder: (String) -> Unit,
    private val stamper: FileStamper,
    private val tags: TagSource,
    private val decode: DecodeCheck,
    private val art: EmbeddedArt,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val folderChanges: () -> Int = { LibraryFolderStore.folderChanges() }
) {

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle(null))
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val lock = Any()
    private var running = false
    private var again = false
    private var lastResult: ScanResult? = null
    // Set by Rescan: the next pass reads unreadable files again, in case the cause has passed.
    @Volatile
    private var retryUnreadable = false

    /** The Rescan button: like [requestScan], and unreadable files are read again too. */
    fun rescan() {
        retryUnreadable = true
        requestScan()
    }

    /** Starts a scan, or asks the running one for exactly one more pass. */
    fun requestScan() {
        synchronized(lock) {
            if (running) {
                again = true
                return
            }
            running = true
        }
        scope.launch { runPasses() }
    }

    private suspend fun runPasses() {
        var finished = false
        try {
            do {
                // A pass that ran across a folder move saw half of it: it writes nothing, and the
                // next pass finds the moved files by their stamps.
                if (!passSafely()) synchronized(lock) { again = true }
            } while (anotherPass())
            finished = true
        } finally {
            // Cancelled or failed past passSafely: a later request must still start a scan.
            if (!finished) {
                synchronized(lock) {
                    running = false
                    again = false
                }
            }
        }
    }

    // Decided under the lock, so a request arriving now either sets the flag in time or starts
    // a scan of its own.
    private fun anotherPass(): Boolean = synchronized(lock) {
        if (again) {
            again = false
            true
        } else {
            running = false
            false
        }
    }

    // False when the pass ran across a move of files in the Library folder.
    private suspend fun passSafely(): Boolean {
        return try {
            runPass()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Room or the file system; the library stays as it was, and the next pass retries.
            // With no pass yet, Home shows Retry rather than building for ever.
            Log.w(TAG, "Scan failed", e)
            _state.value = lastResult?.let { ScanState.Idle(it) }
                ?: ScanState.Failed(folder().absolutePath)
            true
        }
    }

    /**
     * One pass (spec "One pass"), recognising files it has seen before (A2). False, with nothing
     * written, when files in the Library folder were moved while it ran.
     */
    internal suspend fun runPass(): Boolean {
        val changesAtStart = folderChanges()
        val root = folder().absoluteFile
        val retry = retryUnreadable
        retryUnreadable = false
        _state.value = ScanState.Scanning(0, 0)
        val listing = withContext(Dispatchers.IO) {
            val context = coroutineContext
            walkLibrary(root) { context.ensureActive() }
        }
        val stored = StoredLibrary(dao.files(), dao.tracks(), dao.books(), dao.chapters())
        // A folder that's gone, or suddenly holds no audio at all, is far more likely unmounted,
        // renamed or unreadable than emptied: the library, and its book progress, stay. Only the
        // folder the library came from: an empty folder chosen in its place is simply empty.
        val emptied = listing != null && listing.audio.isEmpty() && stored.byPath.isNotEmpty()
        if (listing == null || (emptied && isScannedFolder(root))) {
            _state.value = ScanState.Failed(root.path)
            return true
        }
        val plan = withContext(Dispatchers.IO) {
            planReads(listing.audio, listing.unlisted, stored, retry)
        }
        val results = readAll(plan.toRead)
        val read = identify(plan.toRead, results, plan.vanished, stored)
        val scanned = plan.kept + read
        val reread = read.mapTo(HashSet()) { it.file.path }
        val playlists = withContext(Dispatchers.IO) {
            listing.playlists.mapNotNull { parsePlaylist(root, it) }
        }
        val tracks = scanned.filterIsInstance<ScannedFile.Track>().map { it.track }
        val books = scanned.filterIsInstance<ScannedFile.Book>()
        art.startPass()
        val covers = EmbeddedCovers { id, path ->
            art.coverFor(id, path, File(root, path), path in reread)?.name
        }
        val derived = withContext(Dispatchers.IO) {
            val paths = scanned.associate { it.file.fileId to it.file.path }
            deriveLibrary(tracks, books.map { it.book }, paths, playlists, listing.images, covers)
        }
        if (folderChanges() != changesAtStart) return false
        // Saved first: if writing the library fails, the library left behind stays guarded.
        withContext(Dispatchers.IO) { saveScannedFolder(root.path) }
        dao.replaceLibrary(
            LibraryContents(
                files = scanned.map { it.file },
                tracks = tracks,
                albums = derived.albums,
                artists = derived.artists,
                albumArtists = derived.albumArtists,
                trackArtists = derived.trackArtists,
                genres = derived.genres,
                trackGenres = derived.trackGenres,
                playlists = derived.playlists,
                playlistItems = derived.playlistItems,
                books = derived.books,
                chapters = books.flatMap { it.chapters }
            )
        )
        withContext(Dispatchers.IO) { art.deleteUnused() }
        val listed = derived.playlistItems.mapTo(HashSet()) { it.playlistId }
        val result = ScanResult(
            songs = tracks.size,
            books = books.size,
            playlists = derived.playlists.count { it.playlistId in listed },
            unreadable = scanned.count { it is ScannedFile.Unreadable } +
                results.count { it == null },
            finishedAt = clock()
        )
        lastResult = result
        _state.value = ScanState.Idle(result)
        return true
    }

    // Shared storage ignores case, as FolderSetup does. A library saved before this setting
    // existed is taken to be this folder's, so it stays guarded.
    private fun isScannedFolder(root: File): Boolean =
        scannedFolder()?.equals(root.path, ignoreCase = true) ?: true

    /** A file to read. [fileId] is known when the file is at a path the library already has. */
    private class PendingRead(val found: FoundFile, val stamp: FileStamp, val fileId: String?)

    private class ReadPlan(
        val kept: List<ScannedFile>,
        val toRead: List<PendingRead>,
        /** Known files whose path is gone and that no stamp claimed: tag-key candidates. */
        val vanished: List<LibraryFile>
    )

    // A file at a known path keeps its ID: unchanged, it keeps its rows too. A file at a new path
    // whose stamp is a vanished file's moved with its folder (a folder rename leaves its files'
    // stamps and inodes alone), so it keeps that file's ID and rows. Any other file is read.
    // A known file the pass couldn't see (no stamp, or under a folder it couldn't list) keeps its
    // rows as they were: only a file that's really gone is dropped.
    private fun planReads(
        audio: List<FoundFile>,
        unlisted: List<String>,
        stored: StoredLibrary,
        retry: Boolean
    ): ReadPlan {
        val kept = ArrayList<ScannedFile>()
        val stamped = ArrayList<Pair<FoundFile, FileStamp>>()
        val unseen = HashSet<String>()
        for (found in audio) {
            val stamp = stamper.stampOf(found.file)
            if (stamp != null) stamped.add(found to stamp) else unseen.add(found.path)
        }
        unseen += stored.byPath.keys.filter { path ->
            unlisted.any { path.startsWith("$it/") }
        }
        for (path in unseen) {
            stored.byPath[path]?.let { file -> stored.rowsOf(file, path)?.let(kept::add) }
        }
        val seen = stamped.mapTo(HashSet()) { it.first.path }
        val gone = stored.byPath.values.filter { it.path !in seen && it.path !in unseen }
        val byStamp = gone.groupBy(::stampOf)
            .filterValues { it.size == 1 }
            .mapValuesTo(HashMap()) { it.value.single() }
        val claimed = HashSet<String>()
        val toRead = ArrayList<PendingRead>()
        for ((found, stamp) in stamped) {
            val atPath = stored.byPath[found.path]
            val known = atPath ?: byStamp.remove(stamp)?.takeIf { isSameKind(it, found.path) }
            if (known == null) {
                toRead.add(PendingRead(found, stamp, null))
                continue
            }
            claimed.add(known.fileId)
            val unchanged = atPath == null || stampOf(atPath) == stamp
            val reused = known.takeIf { unchanged }
                ?.takeUnless { retry && it.kind == FileKind.UNREADABLE }
                ?.let { stored.rowsOf(it, found.path) }
            if (reused != null) {
                kept.add(reused)
            } else {
                toRead.add(PendingRead(found, stamp, known.fileId))
            }
        }
        return ReadPlan(kept, toRead, gone.filter { it.fileId !in claimed })
    }

    /**
     * Gives each read file its ID: the one it was read under, or else the ID of the one vanished
     * file with the same tag key (a copy, a re-download, a move to other storage), or else a new
     * one. A key two vanished files share, or two read files share, matches nothing. A known file
     * whose read failed keeps its rows as they were; a new one is left out of this pass only.
     * Either way it's read again next time.
     */
    private fun identify(
        reads: List<PendingRead>,
        results: List<ScannedFile?>,
        vanished: List<LibraryFile>,
        stored: StoredLibrary
    ): List<ScannedFile> {
        val byKey = vanished
            .mapNotNull { file ->
                stored.rowsOf(file, file.path)?.identityKey()?.let { it to file }
            }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
        val newKeys = reads.indices
            .filter { reads[it].fileId == null }
            .mapNotNull { results[it]?.identityKey() }
            .groupingBy { it }
            .eachCount()
        return reads.indices.mapNotNull { index ->
            val read = reads[index]
            val scanned = results[index]
                ?: return@mapNotNull knownRowsOf(read, stored)
            val fileId = read.fileId
                ?: scanned.identityKey()?.takeIf { newKeys[it] == 1 }?.let { byKey[it]?.fileId }
                ?: newId()
            scanned.withIdentity(fileId, read.found.path)
        }
    }

    private suspend fun readAll(files: List<PendingRead>): List<ScannedFile?> {
        val total = files.size
        _state.value = ScanState.Scanning(0, total)
        val done = AtomicInteger()
        val permits = Semaphore(READ_THREADS)
        return coroutineScope {
            files.map { read ->
                async(Dispatchers.IO) {
                    permits.withPermit { readSafely(read) }
                        .also { showProgress(done.incrementAndGet(), total) }
                }
            }.awaitAll()
        }
    }

    // One bad file mustn't fail the pass, every pass. TagLib and BASS come through JNI, so a
    // missing native library throws a LinkageError, not an Exception.
    // A known file whose read failed: its rows as they were, at its path now.
    private fun knownRowsOf(read: PendingRead, stored: StoredLibrary): ScannedFile? {
        val fileId = read.fileId ?: return null
        return stored.byId[fileId]?.let { stored.rowsOf(it, read.found.path) }
    }

    // A file with no ID yet is read under a placeholder; identify() gives it its real one.
    private fun readSafely(read: PendingRead): ScannedFile? = try {
        readAudioFile(read.found, read.stamp, read.fileId ?: UNIDENTIFIED, tags, decode)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read ${read.found.path}", e)
        null
    } catch (e: LinkageError) {
        Log.w(TAG, "Couldn't read ${read.found.path}", e)
        null
    }

    // Reads finish out of order, so a lower count never replaces a higher one.
    private fun showProgress(done: Int, total: Int) {
        _state.update { current ->
            if (current is ScanState.Scanning && current.done >= done) {
                current
            } else {
                ScanState.Scanning(done, total)
            }
        }
    }

    private fun parsePlaylist(root: File, found: FoundFile): Pair<String, ParsedPlaylist>? = try {
        found.path to PlaylistReading.parse(found.file.readText(), found.path, root.path)
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't read playlist ${found.path}", e)
        null
    }

    companion object {
        @Volatile
        private var INSTANCE: LibraryScanner? = null

        /** The app-wide scanner. */
        fun get(context: Context): LibraryScanner =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context.applicationContext).also { INSTANCE = it }
            }

        // The edge where TagLib and BASS come in. Unit tests build the scanner with fakes.
        private fun create(app: Context): LibraryScanner {
            val folders = LibraryFolderStore(app)
            return LibraryScanner(
                dao = LibraryDatabase.getInstance(app).scanDao(),
                folder = folders::folder,
                scannedFolder = folders::scannedFolder,
                saveScannedFolder = folders::saveScannedFolder,
                stamper = OsStamper,
                tags = TagLibSource,
                decode = BassDecodeCheck(app.applicationInfo.nativeLibraryDir),
                art = EmbeddedArt(
                    File(app.filesDir, LibraryFiles.EMBEDDED_ART_FOLDER),
                    TagLibSource
                ),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            )
        }
    }
}

/** The rows the last pass wrote, so known files keep theirs. */
private class StoredLibrary(
    files: List<LibraryFile>,
    tracks: List<LibraryTrack>,
    books: List<LibraryBook>,
    chapters: List<LibraryBookChapter>
) {
    val byPath = files.associateBy { it.path }
    val byId = files.associateBy { it.fileId }
    private val tracks = tracks.associateBy { it.trackId }
    private val books = books.associateBy { it.bookId }
    private val chapters = chapters.groupBy { it.bookId }

    /** [file]'s rows, now at [path]. Null when they're missing, so the file is read again. */
    fun rowsOf(file: LibraryFile, path: String): ScannedFile? {
        val found = file.copy(path = path)
        return when (file.kind) {
            FileKind.TRACK -> tracks[file.fileId]?.let { ScannedFile.Track(found, it) }
            FileKind.BOOK -> books[file.fileId]?.let { book ->
                ScannedFile.Book(found, book, chapters[file.fileId].orEmpty())
            }
            FileKind.UNREADABLE -> ScannedFile.Unreadable(found)
        }
    }
}

private fun stampOf(file: LibraryFile) =
    FileStamp(file.size, file.modifiedSec, file.changedSec, file.inode)

// A file moved into or out of an Audiobooks folder changes kind, so it's read again.
private fun isSameKind(file: LibraryFile, path: String): Boolean = when (file.kind) {
    FileKind.TRACK -> !ScanRules.isBook(path)
    FileKind.BOOK -> ScanRules.isBook(path)
    FileKind.UNREADABLE -> true
}
