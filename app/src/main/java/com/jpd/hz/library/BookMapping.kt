package com.jpd.hz.library

import com.jpd.hz.db.CatalogueBook
import com.jpd.hz.model.MediaItem

private const val TICKS_PER_MS = 10_000L
private const val AUTHOR_KIND = "Author"
private const val AUDIO_STREAM_TYPE = "Audio"
private const val NAME_SEPARATOR = ", "

/** The catalogue rows for the server's audiobooks, which Books to Sync lists. */
fun catalogueBooksFrom(items: List<MediaItem>): List<CatalogueBook> = items.map(::catalogueBookFrom)

/**
 * A book's author: People entries of kind Author, else the album artist, else the artists. Null
 * when none is set, and screens show "Unknown Author" (spec "Server data").
 */
fun bookAuthorOf(item: MediaItem): String? =
    bookAuthorsOf(item).takeIf { it.isNotEmpty() }?.joinToString(NAME_SEPARATOR)

/**
 * The same choice as a list, which sync writes to a book's tags (T2). bookAuthorOf joins it, so
 * the two can't drift apart. Empty when none is set.
 */
fun bookAuthorsOf(item: MediaItem): List<String> {
    val authors = item.people.orEmpty()
        .filter { it.type == AUTHOR_KIND }
        .mapNotNull { person -> person.name?.takeIf { it.isNotBlank() } }
    if (authors.isNotEmpty()) return authors
    item.albumArtist?.takeIf { it.isNotBlank() }?.let { return listOf(it) }
    return item.artists.orEmpty().filter { it.isNotBlank() }
}

// The audio details follow the track mapping (sub-project 2), so the Player's info row works.
private fun catalogueBookFrom(item: MediaItem): CatalogueBook {
    val source = item.mediaSources?.firstOrNull()
    val stream = source?.mediaStreams?.firstOrNull { it.type == AUDIO_STREAM_TYPE }
    return CatalogueBook(
        bookId = item.id,
        name = item.name,
        author = bookAuthorOf(item),
        durationMs = item.runTimeTicks?.let { it / TICKS_PER_MS },
        codec = stream?.codec ?: firstContainer(source?.container ?: item.container),
        bitDepth = stream?.bitDepth,
        sampleRate = stream?.sampleRate,
        bitrate = stream?.bitRate ?: source?.bitrate,
        size = source?.size
    )
}
