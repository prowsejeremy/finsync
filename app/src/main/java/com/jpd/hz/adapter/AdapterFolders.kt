package com.jpd.hz.adapter

private const val PATH_SEPARATOR = '|'
private const val KEY_SEPARATOR = ':'
private const val LINE_SEPARATOR = "\n"
private const val FIRST_NUMBERED_SUFFIX = 2

/**
 * One adapter's folder for one server, saved in `adapter_folders` as `<adapter>:<serverId>|<path>`
 * (spec "Saved settings"). From T3 the path is relative to the Library folder (A1); a full path is
 * one saved before T3.
 */
data class AdapterFolder(val adapter: String, val serverId: String, val path: String)

/** The `adapter_folders` format and the folder rules of D4, with no Android imports. */
object AdapterFolders {

    /** The saved entries, in order. A line that doesn't parse is skipped. */
    fun parse(saved: String?): List<AdapterFolder> =
        saved.orEmpty().lineSequence().mapNotNull(::parseLine).toList()

    fun format(folders: List<AdapterFolder>): String =
        folders.joinToString(LINE_SEPARATOR) { folder ->
            "${folder.adapter}$KEY_SEPARATOR${folder.serverId}$PATH_SEPARATOR${folder.path}"
        }

    fun find(folders: List<AdapterFolder>, adapter: String, serverId: String): AdapterFolder? =
        folders.firstOrNull { it.adapter == adapter && it.serverId == serverId }

    /** [folders] with [folder] in place of any entry for the same adapter and server. */
    fun withFolder(folders: List<AdapterFolder>, folder: AdapterFolder): List<AdapterFolder> =
        folders.filterNot { it.adapter == folder.adapter && it.serverId == folder.serverId } +
            folder

    /**
     * The folder name for a server with no saved folder (D4): the server's name, or with the
     * platform added when that's taken, as `kurage (Jellyfin)`, then `kurage (Jellyfin 2)`.
     */
    fun nameFor(serverName: String, platform: String, isTaken: (String) -> Boolean): String {
        val base = visibleName(serverName, platform)
        if (!isTaken(base)) return base
        val withPlatform = "$base ($platform)"
        if (!isTaken(withPlatform)) return withPlatform
        return generateSequence(FIRST_NUMBERED_SUFFIX) { it + 1 }
            .map { number -> "$base ($platform $number)" }
            .first { !isTaken(it) }
    }

    /**
     * Where an adapter writes for one server (spec "The Jellyfin adapter", "Its folder"):
     * 1. its saved folder;
     * 2. on an install with no saved folder at all, [legacy], the folder it synced to before T2;
     * 3. otherwise `<library>/<name>` by [nameFor]. A library that is, or is inside, a saved
     *    folder moves up to that folder's parent, as T3 makes it, so no adapter's folder ever
     *    holds another's, whose cleanup would delete it. A name is taken when its folder is, or
     *    holds, another server's, or holds anything. [hasFiles] says whether a path exists and
     *    isn't an empty folder.
     */
    fun chooseFolder(
        saved: List<AdapterFolder>,
        adapter: String,
        serverId: String,
        legacy: String?,
        library: String,
        serverName: String,
        platform: String,
        hasFiles: (String) -> Boolean
    ): String {
        find(saved, adapter, serverId)?.let { return it.path }
        if (saved.isEmpty() && legacy != null) return legacy
        val savedPaths = saved.map { it.path }
        val parent = outsideSavedFolders(library, savedPaths)
        val name = nameFor(serverName, platform) { candidate ->
            val path = childPath(parent, candidate)
            savedPaths.any { isSameOrInside(it, path) } || hasFiles(path)
        }
        return childPath(parent, name)
    }

    // Moves up past every saved folder that is, or holds, [library].
    private fun outsideSavedFolders(library: String, savedPaths: List<String>): String {
        var folder = library
        while (true) {
            val holder = savedPaths.firstOrNull { isSameOrInside(folder, it) } ?: return folder
            folder = parentOf(holder) ?: return folder
        }
    }

    /**
     * True when [path] is [folder] or inside it. Shared storage ignores case, so "Kurage"
     * would land in another server's "kurage".
     */
    internal fun isSameOrInside(path: String, folder: String): Boolean {
        val inner = path.trimEnd('/').lowercase()
        val outer = folder.trimEnd('/').lowercase()
        return inner == outer || inner.startsWith("$outer/")
    }

    private fun parentOf(path: String): String? =
        path.trimEnd('/').substringBeforeLast('/', "").takeIf { it.isNotEmpty() }

    private fun childPath(parent: String, name: String): String = "${parent.trimEnd('/')}/$name"

    private fun parseLine(line: String): AdapterFolder? {
        val keyEnd = line.indexOf(PATH_SEPARATOR)
        if (keyEnd < 0) return null
        val key = line.substring(0, keyEnd)
        val path = line.substring(keyEnd + 1)
        val adapterEnd = key.indexOf(KEY_SEPARATOR)
        if (adapterEnd <= 0 || adapterEnd == key.length - 1 || path.isBlank()) return null
        return AdapterFolder(key.substring(0, adapterEnd), key.substring(adapterEnd + 1), path)
    }
}
