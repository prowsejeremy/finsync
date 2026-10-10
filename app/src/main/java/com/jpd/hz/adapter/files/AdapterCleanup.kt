package com.jpd.hz.adapter.files

import java.io.File

/**
 * Cleanup scoped to one adapter's folder (D4, D12). Deletes every file under [folder] that
 * [keep] doesn't list, then the empty folders left below it. [keep] holds absolute paths and is
 * compared ignoring case, because shared storage ignores case. Nothing outside [folder] is
 * touched, and [folder] itself stays. Moved unchanged from SyncEngine.removeOrphanedFiles.
 */
fun cleanUpAdapterFolder(folder: File, keep: Set<String>) {
    val keepLower = keep.mapTo(HashSet()) { it.lowercase() }
    folder.walkTopDown().forEach { file ->
        if (file.isFile && file.absolutePath.lowercase() !in keepLower) file.delete()
    }
    folder.walkBottomUp().forEach { dir ->
        if (dir.isDirectory && dir.absolutePath != folder.absolutePath) {
            if (dir.listFiles()?.isEmpty() == true) dir.delete()
        }
    }
}
