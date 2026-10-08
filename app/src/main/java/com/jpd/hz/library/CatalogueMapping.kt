package com.jpd.hz.library

import com.jpd.hz.db.CatalogueTrack
import com.jpd.hz.model.MediaItem

private const val TICKS_PER_MS = 10_000L
private const val AUDIO_STREAM_TYPE = "Audio"
private const val CONTAINER_SEPARATOR = ','

/**
 * The Jellyfin adapter's copy of the server's tracks, which its Sync card counts and choice
 * screens read (T3: the player reads the Library folder instead). Tracks without an album are
 * kept, so the counts match what sync plans.
 */
fun catalogueTracksFrom(items: List<MediaItem>): List<CatalogueTrack> =
    items.map(::catalogueTrackFrom)

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

// Jellyfin can report a list such as "mov,mp4,m4a"; the first entry names the format. Books use
// it too (3b).
internal fun firstContainer(container: String?): String? =
    container?.split(CONTAINER_SEPARATOR)?.first()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
