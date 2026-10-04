package com.jpd.finsync.library

import com.jpd.finsync.db.CatalogueBook
import com.jpd.finsync.db.CatalogueBookChapter
import com.jpd.finsync.model.MediaItem

private const val TICKS_PER_MS = 10_000L
private const val AUTHOR_KIND = "Author"
private const val AUDIO_STREAM_TYPE = "Audio"
private const val NAME_SEPARATOR = ", "

/** The catalogue rows for the server's audiobooks. */
data class BookRows(val books: List<CatalogueBook>, val chapters: List<CatalogueBookChapter>)

fun bookRowsFrom(items: List<MediaItem>): BookRows = BookRows(
    books = items.map(::catalogueBookFrom),
    chapters = items.flatMap(::chaptersFrom)
)

/**
 * A book's author: People entries of kind Author, else the album artist, else the artists. Null
 * when none is set, and screens show "Unknown Author" (spec "Server data").
 */
fun bookAuthorOf(item: MediaItem): String? {
    val authors = item.people.orEmpty()
        .filter { it.type == AUTHOR_KIND }
        .mapNotNull { person -> person.name?.takeIf { it.isNotBlank() } }
    if (authors.isNotEmpty()) return authors.joinToString(NAME_SEPARATOR)
    item.albumArtist?.takeIf { it.isNotBlank() }?.let { return it }
    val artists = item.artists.orEmpty().filter { it.isNotBlank() }
    return artists.takeIf { it.isNotEmpty() }?.joinToString(NAME_SEPARATOR)
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

// Sorted by start, so positions follow the book. A blank name becomes "Chapter N".
private fun chaptersFrom(item: MediaItem): List<CatalogueBookChapter> =
    item.chapters.orEmpty()
        .sortedBy { it.startPositionTicks ?: 0L }
        .mapIndexed { position, chapter ->
            CatalogueBookChapter(
                bookId = item.id,
                position = position,
                name = chapter.name?.takeIf { it.isNotBlank() } ?: "Chapter ${position + 1}",
                startMs = (chapter.startPositionTicks ?: 0L) / TICKS_PER_MS
            )
        }
