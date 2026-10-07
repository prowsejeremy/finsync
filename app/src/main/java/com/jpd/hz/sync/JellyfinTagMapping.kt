package com.jpd.hz.sync

import com.jpd.hz.library.bookAuthorsOf
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.NameId
import com.jpd.hz.tags.TagValues
import com.jpd.hz.tags.TagWriting

/**
 * Our tags for one Jellyfin item, from data the sync already fetches (spec "Each track or book
 * during a sync", the mapping table). Plain Kotlin, so the table is tested on the JVM.
 */
object JellyfinTagMapping {

    fun valuesOf(item: MediaItem): TagValues =
        if (isBookItem(item)) bookValuesOf(item) else musicValuesOf(item)

    /** The fields sync writes, which its fingerprint is made from. */
    fun fieldsOf(item: MediaItem): Map<String, String> = TagWriting.fieldsOf(valuesOf(item))

    private fun musicValuesOf(item: MediaItem) = TagValues(
        title = item.name,
        artists = usable(item.artists),
        album = item.album,
        albumArtists = namesOf(item.albumArtists),
        genres = usable(item.genres),
        year = item.year,
        trackNumber = item.trackNumber,
        discNumber = item.discNumber
    )

    private fun bookValuesOf(item: MediaItem) = TagValues.forBook(
        title = item.name,
        authors = bookAuthorsOf(item),
        genres = usable(item.genres),
        year = item.year
    )

    // Gson can leave entries null despite the Kotlin types (as CatalogueMapping notes).
    private fun usable(values: List<String>?): List<String> =
        values.orEmpty().filterNot { it.isNullOrBlank() }

    private fun namesOf(credits: List<NameId>?): List<String> =
        credits.orEmpty().filterNot { it.name.isNullOrBlank() }.map { it.name }
}
