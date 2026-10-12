package com.jpd.hz.adapter.folders

import java.io.File

private const val FIELD_SEPARATOR = "\t"
private const val LIBRARY_LINE = "library"
private const val FOLDER_LINE = "folder"
private const val RENAME_LINE = "rename"
// A moved adapter folder is recognised when it holds this share of its recorded files (A4).
private const val MATCH_PERCENT = 90
private const val PERCENT = 100
// How deep below the Library folder a moved adapter folder is looked for.
private const val SEARCH_DEPTH = 4
// Files an adapter's folder holds besides audio: art, playlists and unfinished downloads.
private val NOT_AUDIO = setOf("jpg", "jpeg", "png", "m3u8", "m3u", "part", "nomedia")

/** What happens to an adapter's folder when the Library folder changes. */
sealed class FolderMove {
    /** It's inside the new Library folder already, so it stays. */
    object Keep : FolderMove()

    /**
     * The new Library folder is this adapter's folder or inside it. Refused: the folder would
     * have to move inside itself, and a failed move would mean downloading everything again.
     */
    object Refuse : FolderMove()

    /** It moves to [target]: its own name in the new Library folder, with D4's suffix rule. */
    data class MoveTo(val target: String) : FolderMove()
}

/**
 * A change of Library folder, saved before its first rename so one cut short (a crash, an error)
 * is finished by the next settle rather than redone from scratch.
 * - [library]: the Library folder to save, or null to leave it unsaved (app storage, A6);
 * - [folders]: the adapter folders as they'll be saved, relative to the Library folder (A1);
 * - [renames]: source → target full paths.
 */
data class PendingMove(
    val library: String?,
    val folders: List<AdapterFolder>,
    val renames: List<Pair<String, String>>
)

/** How a set of renames went. Nothing is ever deleted. */
enum class RenameResult {
    /** Every rename worked. */
    DONE,

    /** One failed, and the ones before it were renamed back: nothing changed. */
    UNDONE,

    /** One failed, and one already done couldn't go back: those in [RenameOutcome.done] moved. */
    PARTLY_DONE
}

data class RenameOutcome(val result: RenameResult, val done: List<Pair<File, File>>)

/**
 * The folder rules for setting the Library folder (spec "Changing the Library folder", with the
 * user's two changes of 2026-10-08: a folder inside the adapter's is refused, and a failed move
 * changes nothing). Paths compare ignoring case, as shared storage does.
 */
object FolderMoves {

    /** An adapter's own top-level folders (spec "The Library folder format"). */
    val ADAPTER_CONTENT = listOf("Music", "Audiobooks", "Playlists")

    /**
     * What happens to the adapter folder [folder] when the Library folder becomes [newLibrary].
     * [isTaken] says whether a candidate path is in use, as in [AdapterFolders.nameFor].
     */
    fun moveFor(
        folder: String,
        newLibrary: String,
        platform: String,
        isTaken: (String) -> Boolean
    ): FolderMove {
        if (AdapterFolders.isSameOrInside(newLibrary, folder)) return FolderMove.Refuse
        if (AdapterFolders.isSameOrInside(folder, newLibrary)) return FolderMove.Keep
        val name = AdapterFolders.nameFor(nameOf(folder), platform) { candidate ->
            isTaken(childPath(newLibrary, candidate))
        }
        return FolderMove.MoveTo(childPath(newLibrary, name))
    }

    /**
     * Renames each source to its target, in order: one rename per folder, which is instant on
     * the same storage. A source that doesn't exist is skipped, and a target that exists counts
     * as a failure, so nothing is ever merged or replaced.
     */
    fun renameAll(renames: List<Pair<File, File>>): RenameOutcome {
        val done = ArrayList<Pair<File, File>>()
        for ((source, target) in renames) {
            if (!source.exists()) continue
            target.parentFile?.mkdirs()
            if (target.exists() || !source.renameTo(target)) return undo(done)
            done.add(source to target)
        }
        return RenameOutcome(RenameResult.DONE, done)
    }

    // Last first, so each folder goes back where it was.
    private fun undo(done: List<Pair<File, File>>): RenameOutcome {
        val stuck = done.reversed().filterNot { (source, target) -> target.renameTo(source) }
        return if (stuck.isEmpty()) {
            RenameOutcome(RenameResult.UNDONE, emptyList())
        } else {
            RenameOutcome(RenameResult.PARTLY_DONE, stuck.reversed())
        }
    }

    /** [move] as one line per part, tab-separated: Android paths never hold tabs. */
    fun encode(move: PendingMove): String = buildList {
        move.library?.let { add(listOf(LIBRARY_LINE, it)) }
        move.folders.forEach { add(listOf(FOLDER_LINE, it.adapter, it.serverId, it.path)) }
        move.renames.forEach { (source, target) -> add(listOf(RENAME_LINE, source, target)) }
    }.joinToString("\n") { it.joinToString(FIELD_SEPARATOR) }

    /** The move [encode] wrote, or null when [text] doesn't hold one. */
    fun decode(text: String?): PendingMove? {
        if (text.isNullOrEmpty()) return null
        var library: String? = null
        val folders = ArrayList<AdapterFolder>()
        val renames = ArrayList<Pair<String, String>>()
        for (line in text.lines().filter { it.isNotEmpty() }) {
            val fields = line.split(FIELD_SEPARATOR)
            when {
                fields[0] == LIBRARY_LINE && fields.size == 2 -> library = fields[1]
                fields[0] == FOLDER_LINE && fields.size == 4 ->
                    folders.add(AdapterFolder(fields[1], fields[2], fields[3]))
                fields[0] == RENAME_LINE && fields.size == 3 -> renames.add(fields[1] to fields[2])
                else -> return null
            }
        }
        return PendingMove(library, folders, renames)
    }

    /** A saved adapter folder as a full path: relative to [library] (A1), or a pre-T3 full one. */
    fun resolve(saved: String, library: String): String =
        if (saved.startsWith('/')) saved else childPath(library, saved)

    /** [path] relative to [library] when it's inside it (not the folder itself), else null. */
    fun relativeOf(path: String, library: String): String? {
        val root = library.trimEnd('/')
        val inside = path.trimEnd('/')
        if (!inside.startsWith("$root/", ignoreCase = true)) return null
        return inside.substring(root.length + 1).takeIf { it.isNotEmpty() }
    }

    /**
     * True when [folder] is the adapter's folder, moved there by hand (A4): it holds at least 90%
     * of [records] (relative path to size), and at least one, and at least 90% of the files in it
     * that could be audio are recorded ones. The second check keeps a folder of the user's own
     * music, which cleanup would empty, from passing for the adapter's.
     */
    fun holdsRecords(folder: File, records: Map<String, Long>): Boolean {
        val found = records.count { (path, size) ->
            File(folder, path).let { it.isFile && it.length() == size }
        }
        if (found == 0 || found * PERCENT < records.size * MATCH_PERCENT) return false
        val files = folder.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() !in NOT_AUDIO }
            .count()
        return found * PERCENT >= files * MATCH_PERCENT
    }

    /**
     * The adapter's folder moved by hand somewhere under [root]: the first folder, in path order
     * and at most four levels down, that holds a `Music`, `Audiobooks` or `Playlists` folder and
     * passes [holdsRecords]. A folder shaped like one that doesn't pass is searched below too.
     * Hidden folders are skipped.
     */
    fun findAdapterFolder(root: File, records: Map<String, Long>): File? {
        var level = root.listFiles().orEmpty().filter(::isVisibleFolder)
        repeat(SEARCH_DEPTH) {
            val next = ArrayList<File>()
            for (folder in level.sortedBy { it.name.lowercase() }) {
                val children = folder.listFiles().orEmpty().filter(::isVisibleFolder)
                val shaped = children.any { child ->
                    ADAPTER_CONTENT.any { it.equals(child.name, ignoreCase = true) }
                }
                if (shaped && holdsRecords(folder, records)) return folder
                next.addAll(children)
            }
            level = next
        }
        return null
    }

    private fun isVisibleFolder(file: File): Boolean =
        file.isDirectory && !file.name.startsWith('.')

    private fun nameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')

    private fun childPath(parent: String, name: String): String = "${parent.trimEnd('/')}/$name"
}

/** True for a path that exists and isn't an empty folder. One that can't be listed counts. */
fun hasFiles(path: String): Boolean {
    val file = File(path)
    if (!file.exists()) return false
    val names = file.list() ?: return true
    return names.isNotEmpty()
}
