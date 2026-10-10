package com.jpd.hz.adapter.run

import com.jpd.hz.adapter.ExtraFile
import com.jpd.hz.adapter.files.LibraryLayout
import com.jpd.hz.adapter.files.PlaylistEntry
import com.jpd.hz.adapter.files.PlaylistFiles

private const val MS_PER_SECOND = 1_000L

/**
 * Every path a run keeps, relative to the connection's folder (spec "The sync run", step 5): each
 * planned item, a cover beside each planned item of an album or book, each extra, and each planned
 * playlist's file and cover. Cleanup deletes everything else in the folder.
 */
fun keepSetOf(plan: SyncPlan, extras: List<ExtraFile>): Set<String> {
    val paths = HashSet<String>()
    plan.items.forEach { paths.add(it.path) }
    for (cover in plan.covers) {
        cover.items.forEach { paths.add(LibraryLayout.coverBeside(it.path)) }
    }
    extras.forEach { paths.add(it.path) }
    playlistFileNamesOf(plan).values.forEach { name ->
        paths.add(LibraryLayout.playlistFilePath(name))
        paths.add(LibraryLayout.playlistCoverPath(name))
    }
    return paths
}

/** Each planned playlist's file name, without extension, by group ID. */
fun playlistFileNamesOf(plan: SyncPlan): Map<String, String> =
    PlaylistFiles.fileNamesOf(plan.playlists.map { it.group.id to it.group.name })

/** A planned playlist's file text (spec "Playlist files"). */
fun playlistTextOf(playlist: PlannedPlaylist): String =
    PlaylistFiles.contentOf(
        playlist.group.name,
        playlist.items.map { item ->
            PlaylistEntry(
                path = LibraryLayout.playlistEntryPath(item.path),
                seconds = item.durationMs?.let { it / MS_PER_SECOND },
                label = item.label
            )
        }
    )

/**
 * Whether the run may write an item at [path] (spec "The sync run", step 6): relative, with no
 * `..` segment, so nothing lands outside the connection's folder. Hidden names are allowed; the
 * scanner skips them.
 */
fun isSafePath(path: String): Boolean =
    path.isNotBlank() && !path.startsWith('/') && path.split('/').none { it == ".." }
