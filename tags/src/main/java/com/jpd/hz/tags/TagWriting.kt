package com.jpd.hz.tags

private const val MAX_YEAR = 9999
private const val YEAR_DIGITS = 4

/**
 * The values an adapter writes for one file. A null or blank value, or an empty list, is left
 * out, so the file keeps its own (D2).
 */
data class TagValues(
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumArtists: List<String> = emptyList(),
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null
) {
    companion object {
        /** A book's title is also its album, and its authors are both artist fields. */
        fun forBook(title: String?, authors: List<String>, genres: List<String>, year: Int?) =
            TagValues(
                title = title,
                artists = authors,
                album = title,
                albumArtists = authors,
                genres = genres,
                year = year
            )
    }
}

object TagWriting {
    /** The fields to hand to [TagLibBridge.write], keyed by [TagField] names. */
    fun fieldsOf(values: TagValues): Map<String, String> = buildMap {
        putText(TagField.TITLE, values.title)
        putList(TagField.ARTIST, values.artists)
        putText(TagField.ALBUM, values.album)
        putList(TagField.ALBUM_ARTIST, values.albumArtists)
        putList(TagField.GENRE, values.genres)
        values.year?.takeIf { it in 1..MAX_YEAR }?.let {
            put(TagField.DATE, it.toString().padStart(YEAR_DIGITS, '0'))
        }
        values.trackNumber?.takeIf { it >= 0 }?.let { put(TagField.TRACK_NUMBER, it.toString()) }
        values.discNumber?.takeIf { it >= 0 }?.let { put(TagField.DISC_NUMBER, it.toString()) }
    }

    private fun MutableMap<String, String>.putText(field: String, value: String?) {
        value?.trim()?.takeIf { it.isNotEmpty() }?.let { put(field, it) }
    }

    private fun MutableMap<String, String>.putList(field: String, values: List<String>) {
        val kept = values.map(String::trim).filter(String::isNotEmpty)
            .distinctBy(Normalising::normalise)
        if (kept.isNotEmpty()) put(field, kept.joinToString(VALUE_SEPARATOR))
    }
}
