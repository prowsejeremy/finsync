package com.jpd.hz.tags

private const val AUTHOR_SEPARATOR = ", "
// [0-9], not \d: on Android \d also matches other scripts' digits.
private val FOUR_DIGITS = Regex("""[0-9]{4}""")
private val LEADING_NUMBER = Regex("""^\s*([0-9]+)""")

/** What the scanner keeps from a music file's tags. */
data class TrackTags(
    val title: String,
    val artists: List<String>,
    val album: String?,
    val albumArtists: List<String>,
    val genres: List<String>,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?
)

/** What the scanner keeps from a book file's tags. */
data class BookTags(
    val title: String,
    val authors: List<String>,
    val genres: List<String>,
    val year: Int?
) {
    /** The author line screens show. */
    val author: String get() = authors.joinToString(AUTHOR_SEPARATOR)
}

object TagReading {
    /**
     * Several values from one field (D5). Each native value is split on ";" and trimmed, and blanks
     * and repeats (compared as normalised names, so the first spelling stays) are dropped. "/", ","
     * and " feat. " never split, so "AC/DC" stays whole.
     */
    fun splitValues(values: List<String>?): List<String> =
        values.orEmpty()
            .flatMap { it.split(';') }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinctBy(Normalising::normalise)

    /** [fileName] is the name without its folder, such as "01 One More Time.flac". */
    fun track(fields: Map<String, List<String>>, fileName: String): TrackTags {
        val artists = splitValues(fields[TagField.ARTIST])
        return TrackTags(
            title = firstValue(fields, TagField.TITLE) ?: fileTitle(fileName),
            artists = artists,
            album = firstValue(fields, TagField.ALBUM),
            albumArtists = splitValues(fields[TagField.ALBUM_ARTIST]).ifEmpty { artists.take(1) },
            genres = splitValues(fields[TagField.GENRE]),
            year = yearOf(firstValue(fields, TagField.DATE)),
            trackNumber = leadingNumber(firstValue(fields, TagField.TRACK_NUMBER)),
            discNumber = leadingNumber(firstValue(fields, TagField.DISC_NUMBER))
        )
    }

    /** [fileName] is the name without its folder, such as "Beholding.m4b". */
    fun book(fields: Map<String, List<String>>, fileName: String): BookTags = BookTags(
        title = firstValue(fields, TagField.ALBUM)
            ?: firstValue(fields, TagField.TITLE)
            ?: fileTitle(fileName),
        authors = splitValues(fields[TagField.ALBUM_ARTIST])
            .ifEmpty { splitValues(fields[TagField.ARTIST]) },
        genres = splitValues(fields[TagField.GENRE]),
        year = yearOf(firstValue(fields, TagField.DATE))
    )

    /** The first run of four digits: "2013-05-17" and "℗ 2013" both read as 2013. */
    fun yearOf(date: String?): Int? = date?.let { FOUR_DIGITS.find(it)?.value?.toInt() }

    /** The leading whole number: "3/12" reads as 3, and "A1" as nothing. */
    fun leadingNumber(value: String?): Int? =
        value?.let { LEADING_NUMBER.find(it)?.groupValues?.get(1)?.toIntOrNull() }

    /** The file name without its extension. A name that is all extension stays whole. */
    fun fileTitle(fileName: String): String = fileName.substringBeforeLast('.').ifEmpty { fileName }

    // Single-value fields aren't split on ";", because a title or album can contain one.
    private fun firstValue(fields: Map<String, List<String>>, field: String): String? =
        fields[field].orEmpty().map(String::trim).firstOrNull(String::isNotEmpty)
}
