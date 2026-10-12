package com.jpd.hz.adapter.folders

import android.content.Context
import com.jpd.hz.adapter.Connection
import com.jpd.hz.library.LibraryFolderStore
import java.io.File

/**
 * Where each connection syncs (spec "The Jellyfin adapter", "Its folder"): its `adapter_folders`
 * entry, fixed the first time it's needed. A new connection's folder goes in the Library folder,
 * named after it, with D4's suffix rule.
 */
object ConnectionFolders {

    fun folderFor(context: Context, connection: Connection, platformName: String): File {
        val store = AdapterFolderStore(context)
        val folders = LibraryFolderStore(context)
        val savedLibrary = folders.saved()
        // Only while no Library folder is saved: publicDefault() creates Media/hz, which would
        // otherwise come back empty after the user moved it away.
        val publicLibrary = if (savedLibrary == null) folders.publicDefault() else null
        val library = savedLibrary?.let(::File) ?: publicLibrary ?: folders.appDefault()
        // Saved relative to the Library folder (A1).
        store.pathFor(connection.platform, connection.sourceId)?.let {
            return File(FolderMoves.resolve(it, library.absolutePath))
        }
        val path = AdapterFolders.chooseFolder(
            saved = store.all().map { it.copy(path = FolderMoves.resolve(it.path, library.path)) },
            adapter = connection.platform,
            serverId = connection.sourceId,
            library = library.absolutePath,
            serverName = connection.name,
            platform = platformName,
            hasFiles = ::hasFiles
        )
        // A folder in app storage, used only without all-files access, isn't saved. Once access
        // is granted, the next call settles on public Media/hz.
        if (savedLibrary != null || publicLibrary != null) {
            val saved = FolderMoves.relativeOf(path, library.absolutePath) ?: path
            store.save(AdapterFolder(connection.platform, connection.sourceId, saved))
        }
        return File(path)
    }
}

/**
 * Why a run must not use [folder], or null when it may (T3, A5). A folder that is, or holds, the
 * Library folder would have its cleanup delete the user's own files. A saved folder that's gone
 * while the connection's records ([hasRecords]) name files in it was moved or unmounted:
 * recreating it would download everything again. [name] is the connection's, such as "kurage".
 */
fun syncFolderProblemOf(
    name: String,
    folder: String,
    exists: Boolean,
    hasRecords: Boolean,
    library: String
): String? {
    if (AdapterFolders.isSameOrInside(library, folder)) {
        return "$name's folder $folder holds the Library folder. Nothing was synced."
    }
    if (!exists && hasRecords) {
        return "Can't find $name's folder $folder. Nothing was synced."
    }
    return null
}
