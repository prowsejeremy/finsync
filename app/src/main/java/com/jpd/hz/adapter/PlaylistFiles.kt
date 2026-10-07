package com.jpd.hz.adapter

private const val HEADER = "#EXTM3U"
private const val NAME_LINE = "#PLAYLIST:"
private const val ENTRY_LINE = "#EXTINF:"
// The usual M3U value for an unknown length.
private const val UNKNOWN_SECONDS = -1L
private const val FIRST_REPEAT = 2
private const val FALLBACK_NAME = "Playlist"
private val LINE_BREAKS = Regex("[\\r\\n]+")

/** One track in a playlist file: its path relative to the playlist file, with `/` separators. */
data class PlaylistEntry(val path: String, val seconds: Long?, val label: String)

/** Playlist files as adapters write them (spec "Playlist files"). */
object PlaylistFiles {

    const val EXTENSION = "m3u8"

    /** The file's text, with `\n` line endings. Write it as UTF-8 with no byte-order mark. */
    fun contentOf(name: String, entries: List<PlaylistEntry>): String = buildString {
        append(HEADER).append('\n')
        append(NAME_LINE).append(oneLine(name)).append('\n')
        for (entry in entries) {
            append(ENTRY_LINE).append(entry.seconds ?: UNKNOWN_SECONDS).append(',')
                .append(oneLine(entry.label)).append('\n')
            append(entry.path).append('\n')
        }
    }

    /**
     * A file name, without its extension, for each playlist ID in [playlists] (ID to name): the
     * sanitised name, then ` (2)`, ` (3)` when names collide. Names are compared ignoring case,
     * as shared storage does. Playlists are numbered by name, then ID, so the numbers stay put
     * from one sync to the next.
     */
    fun fileNamesOf(playlists: List<Pair<String, String>>): Map<String, String> {
        val used = HashSet<String>()
        val names = LinkedHashMap<String, String>()
        val ordered = playlists
            .map { (id, name) -> id to visibleName(name, FALLBACK_NAME) }
            .sortedWith(compareBy({ it.second.lowercase() }, { it.first }))
        for ((id, base) in ordered) {
            var candidate = base
            var number = FIRST_REPEAT
            while (!used.add(candidate.lowercase())) {
                candidate = "$base ($number)"
                number++
            }
            names[id] = candidate
        }
        return names
    }

    private fun oneLine(text: String): String = text.replace(LINE_BREAKS, " ").trim()
}
