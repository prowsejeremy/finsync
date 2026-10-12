package com.jpd.hz.library.scan

import java.io.File

/** Path order: ignoring case first, so "abc" and "ABD" sort as a person expects, then exactly. */
internal val PATH_ORDER: Comparator<String> =
    String.CASE_INSENSITIVE_ORDER.thenComparator { a, b -> a.compareTo(b) }

/** A file the walk found. [path] is relative to the Library folder, with "/" separators. */
data class FoundFile(val path: String, val file: File)

/** What one walk of the Library folder found, each list in path order. */
data class FolderListing(
    val audio: List<FoundFile>,
    val playlists: List<FoundFile>,
    /** Each image's path, keyed by its lower-cased path (see [ScanRules]). */
    val images: Map<String, String>,
    /** Folders that couldn't be listed: what's known below them is kept, not dropped. */
    val unlisted: List<String> = emptyList()
)

/**
 * Walks the Library folder (spec "Scanning"), skipping hidden files and folders and any folder
 * holding .nomedia. Null when [root] can't be listed, so an unmounted card or a missing folder
 * never empties the library. A folder below it that can't be listed is noted in
 * [FolderListing.unlisted]. [checkCancelled] runs before each folder and throws to stop the walk.
 */
fun walkLibrary(root: File, checkCancelled: () -> Unit = {}): FolderListing? {
    val top = root.listFiles() ?: return null
    val audio = ArrayList<FoundFile>()
    val playlists = ArrayList<FoundFile>()
    val images = HashMap<String, String>()
    val unlisted = ArrayList<String>()
    val pending = ArrayDeque<Pair<String, Array<File>>>()
    pending.addLast("" to top)
    while (pending.isNotEmpty()) {
        checkCancelled()
        val (folder, children) = pending.removeFirst()
        if (children.any { ScanRules.isNoMedia(it.name) }) continue
        for (child in children) {
            if (ScanRules.isHidden(child.name)) continue
            val path = ScanRules.childOf(folder, child.name)
            if (child.isDirectory) {
                val listed = child.listFiles()
                if (listed != null) pending.addLast(path to listed) else unlisted.add(path)
                continue
            }
            when {
                ScanRules.isAudio(child.name) -> audio.add(FoundFile(path, child))
                ScanRules.isPlaylist(child.name) -> playlists.add(FoundFile(path, child))
                ScanRules.isImage(child.name) -> images[path.lowercase()] = path
            }
        }
    }
    return FolderListing(
        audio = audio.sortedWith(compareBy(PATH_ORDER) { it.path }),
        playlists = playlists.sortedWith(compareBy(PATH_ORDER) { it.path }),
        images = images,
        unlisted = unlisted
    )
}
