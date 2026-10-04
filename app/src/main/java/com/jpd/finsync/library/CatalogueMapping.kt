package com.jpd.finsync.library

import com.jpd.finsync.db.CatalogueAlbum
import com.jpd.finsync.db.CatalogueTrack
import com.jpd.finsync.model.MediaItem

private const val TICKS_PER_MS = 10_000L
private const val UNKNOWN_ALBUM = "Unknown Album"
private const val AUDIO_STREAM_TYPE = "Audio"
private const val CONTAINER_SEPARATOR = ','

data class Catalogue(val albums: List<CatalogueAlbum>, val tracks: List<CatalogueTrack>)

/**
 * Turns the server's audio items into catalogue rows. Albums come from grouping tracks by
 * albumId, as SyncEngine does; tracks without an album are kept for the future Songs screen.
 */
fun catalogueFrom(items: List<MediaItem>): Catalogue {
    val tracks = items.map(::catalogueTrackFrom)
    val albums = items
        .filter { it.albumId != null }
        .groupBy { it.albumId!! }
        .map { (albumId, albumItems) -> catalogueAlbumFrom(albumId, albumItems) }
    return Catalogue(albums, tracks)
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

// Jellyfin can report a list such as "mov,mp4,m4a"; the first entry names the format.
private fun firstContainer(container: String?): String? =
    container?.split(CONTAINER_SEPARATOR)?.first()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
