package com.jpd.finsync.sync

import com.jpd.finsync.model.MediaItem
import java.io.File

// SyncEngine's path rules, moved here unchanged (3b) so the files-to-keep rule can be tested
// without Android. SyncEngine still calls them by the same names.

internal fun buildRelativePath(item: MediaItem): String {
    val artist = sanitizeFilename(item.albumArtist ?: item.artists?.firstOrNull() ?: "Unknown Artist")
    val album  = sanitizeFilename(item.album ?: "Unknown Album")
    // Use the filename from the server if available, since it may contain a track number prefix that we don't want to lose.
    // If not, construct a filename ourselves.
    val filename = item.path
        ?.substringAfterLast('/')
        ?.let { sanitizeFilename(it) }
        ?: run {
            val track = item.trackNumber?.let { "%02d ".format(it) } ?: ""
            val name  = sanitizeFilename(item.name)
            val ext   = resolveExtension(item)
            "$track$name.$ext"
        }
    return "$artist/$album/$filename"
}

internal fun resolveExtension(item: MediaItem): String {
    item.path?.let { serverPath ->
        val ext = serverPath.substringAfterLast('.', "").lowercase()
        if (ext.isNotBlank() && ext.length <= 5 && !ext.contains('/')) return ext
    }
    item.container?.let { c ->
        val ext = c.split(',').first().trim().lowercase()
        if (ext.isNotBlank()) return ext
    }
    return "mp3"
}

internal fun buildArtworkPath(item: MediaItem): String {
    val artist = sanitizeFilename(item.albumArtist ?: item.artists?.firstOrNull() ?: "Unknown Artist")
    val album  = sanitizeFilename(item.album ?: "Unknown Album")
    return "$artist/$album/folder.jpg"
}

internal fun sanitizeFilename(name: String): String =
    name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()

/**
 * Every file sync keeps (spec "Order, cleanup and failures"): each planned track and its album's
 * folder art. The orphan cleanup deletes anything else in the sync folder.
 */
internal fun filesToKeep(syncDir: File, plan: SyncPlan): Set<String> {
    val paths = mutableSetOf<String>()
    for (item in plan.tracks) {
        paths.add(File(syncDir, buildRelativePath(item)).absolutePath)
        paths.add(File(syncDir, buildArtworkPath(item)).absolutePath)
    }
    return paths
}
