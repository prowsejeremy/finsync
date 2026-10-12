package com.jpd.hz.library.scan

private const val NAME_LINE = "#PLAYLIST:"
private const val COMMENT = '#'
private const val BYTE_ORDER_MARK = '﻿'
private const val URL_MARK = "://"
private const val PARENT = ".."
private const val CURRENT = "."
private val LINE_BREAKS = Regex("\r\n|\r|\n")
private val DRIVE_LETTER = Regex("^[A-Za-z]:/")

/** A playlist file as the player reads it: its name and its entries' paths, in order. */
data class ParsedPlaylist(val name: String, val entries: List<String>)

/** How the player reads playlist files (spec "Playlist files"), with no Android imports. */
object PlaylistReading {

    /**
     * [path] is the playlist file's path in the Library folder, and [libraryPath] the folder's
     * absolute path. Entries become Library-relative paths: relative entries are resolved from
     * the playlist's own folder, `\` counts as a separator and `..` segments are resolved. An
     * absolute path inside the Library folder loses the folder's part; any other absolute path,
     * a URL, or an entry leading out of the Library folder is skipped. `#` lines other than the
     * name are ignored.
     */
    fun parse(text: String, path: String, libraryPath: String): ParsedPlaylist {
        var name: String? = null
        val entries = ArrayList<String>()
        val folder = ScanRules.folderOf(path)
        for (rawLine in text.trimStart(BYTE_ORDER_MARK).split(LINE_BREAKS)) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith(NAME_LINE) -> if (name == null) {
                    name = line.removePrefix(NAME_LINE).trim().takeIf { it.isNotEmpty() }
                }
                line.startsWith(COMMENT) -> Unit
                else -> resolve(line, folder, libraryPath)?.let(entries::add)
            }
        }
        val fileTitle = ScanRules.fileNameOf(path).substringBeforeLast('.')
        return ParsedPlaylist(name ?: fileTitle, entries)
    }

    /** One entry as a Library-relative path, or null when it points outside the folder. */
    fun resolve(entry: String, playlistFolder: String, libraryPath: String): String? {
        val slashed = entry.replace('\\', '/')
        if (slashed.contains(URL_MARK) || DRIVE_LETTER.containsMatchIn(slashed)) return null
        if (slashed.startsWith('/')) {
            val inside = relativeTo(normalised(slashed) ?: return null, libraryPath)
            return inside?.takeIf { it.isNotEmpty() }
        }
        return normalised(ScanRules.childOf(playlistFolder, slashed))?.takeIf { it.isNotEmpty() }
    }

    // Resolves "." and ".." and drops empty segments. Null when ".." climbs above the start.
    private fun normalised(path: String): String? {
        val segments = ArrayList<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", CURRENT -> Unit
                PARENT -> {
                    if (segments.isEmpty()) return null
                    segments.removeAt(segments.size - 1)
                }
                else -> segments.add(segment)
            }
        }
        val joined = segments.joinToString("/")
        return if (path.startsWith('/')) "/$joined" else joined
    }

    // Shared storage ignores case, so the folder's part may differ in case.
    private fun relativeTo(absolute: String, libraryPath: String): String? {
        val library = libraryPath.trimEnd('/')
        if (!absolute.startsWith("$library/", ignoreCase = true)) return null
        return absolute.substring(library.length + 1)
    }
}
