package com.jpd.finsync.library

import com.jpd.finsync.db.CatalogueAlbum
import com.jpd.finsync.db.CatalogueAlbumArtist
import com.jpd.finsync.db.CatalogueArtist
import com.jpd.finsync.db.CatalogueGenre
import com.jpd.finsync.db.CatalogueTrack
import com.jpd.finsync.db.CatalogueTrackArtist
import com.jpd.finsync.db.CatalogueTrackGenre
import com.jpd.finsync.model.MediaItem
import com.jpd.finsync.model.NameId

private const val TICKS_PER_MS = 10_000L
private const val UNKNOWN_ALBUM = "Unknown Album"
private const val AUDIO_STREAM_TYPE = "Audio"
private const val CONTAINER_SEPARATOR = ','

data class Catalogue(
    val albums: List<CatalogueAlbum>,
    val tracks: List<CatalogueTrack>,
    val artists: List<CatalogueArtist>,
    val albumArtists: List<CatalogueAlbumArtist>,
    val trackArtists: List<CatalogueTrackArtist>,
    val genres: List<CatalogueGenre>,
    val trackGenres: List<CatalogueTrackGenre>
)

/**
 * Turns the server's audio items into catalogue rows. Albums come from grouping tracks by
 * albumId, as SyncEngine does; tracks without an album are kept for the Songs screen.
 */
fun catalogueFrom(items: List<MediaItem>): Catalogue {
    val albumGroups = items.filter { it.albumId != null }.groupBy { it.albumId!! }
    return Catalogue(
        albums = albumGroups.map { (albumId, albumItems) ->
            catalogueAlbumFrom(albumId, albumItems)
        },
        tracks = items.map(::catalogueTrackFrom),
        artists = artistsFrom(items),
        albumArtists = albumGroups.flatMap { (albumId, albumItems) ->
            albumArtistsFrom(albumId, albumItems)
        },
        trackArtists = items.flatMap(::trackArtistsFrom),
        genres = genresFrom(items),
        trackGenres = items.flatMap(::trackGenresFrom)
    )
}

private fun catalogueTrackFrom(item: MediaItem): CatalogueTrack {
    val source = item.mediaSources?.firstOrNull()
    val stream = source?.mediaStreams?.firstOrNull { it.type == AUDIO_STREAM_TYPE }
    return CatalogueTrack(
        itemId = item.id,
        albumId = item.albumId,
        name = item.name,
        artistNames = item.artists ?: item.artistItems?.map { it.name } ?: emptyList(),
        artistIds = item.artistItems?.map { it.id } ?: emptyList(),
        albumArtist = item.albumArtist,
        discNumber = item.discNumber,
        trackNumber = item.trackNumber,
        durationMs = item.runTimeTicks?.let { it / TICKS_PER_MS },
        codec = stream?.codec ?: firstContainer(source?.container ?: item.container),
        bitDepth = stream?.bitDepth,
        sampleRate = stream?.sampleRate,
        bitrate = stream?.bitRate ?: source?.bitrate,
        size = source?.size
    )
}

private fun catalogueAlbumFrom(albumId: String, items: List<MediaItem>): CatalogueAlbum {
    val first = items.first()
    return CatalogueAlbum(
        albumId = albumId,
        name = first.album ?: UNKNOWN_ALBUM,
        albumArtist = first.albumArtist ?: first.artists?.firstOrNull(),
        year = items.firstNotNullOfOrNull { it.year }
    )
}

// An album's album artists are the union of its tracks' credits, in first-seen order.
private fun albumArtistsFrom(albumId: String, items: List<MediaItem>): List<CatalogueAlbumArtist> =
    items.flatMap { usableCredits(it.albumArtists) }
        .distinctBy { it.id }
        .mapIndexed { position, credit -> CatalogueAlbumArtist(albumId, credit.id, position) }

private fun trackArtistsFrom(item: MediaItem): List<CatalogueTrackArtist> =
    usableCredits(item.artistItems)
        .distinctBy { it.id }
        .mapIndexed { position, credit -> CatalogueTrackArtist(item.id, credit.id, position) }

private fun trackGenresFrom(item: MediaItem): List<CatalogueTrackGenre> =
    usableCredits(item.genreItems)
        .distinctBy { it.id }
        .map { CatalogueTrackGenre(item.id, it.id) }

// Album artists and track artists share one table. distinctBy keeps the first credit seen, so
// the first name for an ID wins.
private fun artistsFrom(items: List<MediaItem>): List<CatalogueArtist> =
    items.flatMap { usableCredits(it.albumArtists) + usableCredits(it.artistItems) }
        .distinctBy { it.id }
        .map { CatalogueArtist(it.id, it.name) }

private fun genresFrom(items: List<MediaItem>): List<CatalogueGenre> =
    items.flatMap { usableCredits(it.genreItems) }
        .distinctBy { it.id }
        .map { CatalogueGenre(it.id, it.name) }

// Gson can leave either field null despite the Kotlin types, and a blank ID would merge
// unrelated credits into one row.
private fun usableCredits(credits: List<NameId>?): List<NameId> =
    credits.orEmpty().filterNot { it.id.isNullOrBlank() || it.name.isNullOrBlank() }

// Jellyfin can report a list such as "mov,mp4,m4a"; the first entry names the format.
private fun firstContainer(container: String?): String? =
    container?.split(CONTAINER_SEPARATOR)?.first()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
