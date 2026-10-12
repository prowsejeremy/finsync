package com.jpd.hz.tags

import java.util.Locale

// \p{Z} adds the Unicode spaces the JVM's \s leaves out, such as the no-break space some taggers
// write. Android's regex engine rejects the (?U) flag that would otherwise do it.
private val WHITESPACE_RUN = Regex("""[\s\p{Z}]+""")
private const val ALBUM_ID_SEPARATOR = '\u001F'

/** Normalised names are for IDs only; screens show the original spelling (D8). */
object Normalising {
    fun normalise(value: String): String =
        value.trim().replace(WHITESPACE_RUN, " ").lowercase(Locale.ROOT)

    /**
     * Tracks with the same album ID form one album, so an album split across CD1/ and CD2/ stays
     * whole, and same-named albums by different album artists stay apart. Null for a track with
     * no album, which appears only in Songs.
     */
    fun albumIdOf(albumArtists: List<String>, album: String?): String? {
        val name = album?.let(::normalise)?.takeIf { it.isNotEmpty() } ?: return null
        return normalise(albumArtists.joinToString(VALUE_SEPARATOR)) + ALBUM_ID_SEPARATOR + name
    }
}
