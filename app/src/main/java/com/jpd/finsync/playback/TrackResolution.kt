package com.jpd.finsync.playback

import com.jpd.finsync.library.PlayableSource

private const val ARTIST_SEPARATOR = ", "

/** Everything needed to play and describe one downloaded track. */
data class ResolvedTrack(
    val itemId: String,
    val path: String,
    val title: String,
    val artists: String?,
    val albumTitle: String?,
    val albumId: String?,
    val albumArtist: String?,
    val artworkPath: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long?,
    val codec: String?,
    val bitDepth: Int?,
    val sampleRate: Int?,
    val bitrate: Int?,
    val fileSize: Long
)

/**
 * Null when the track has no catalogue or sync row ([source] is null) or its file is gone
 * ([fileLength] returns null), so playback skips it. The size comes from the file itself.
 */
fun resolveTrack(source: PlayableSource?, fileLength: (String) -> Long?): ResolvedTrack? {
    if (source == null) return null
    val size = fileLength(source.localPath) ?: return null
    val track = source.track
    return ResolvedTrack(
        itemId = track.itemId,
        path = source.localPath,
        title = track.name,
        artists = track.artistNames.takeIf { it.isNotEmpty() }?.joinToString(ARTIST_SEPARATOR)
            ?: track.albumArtist,
        albumTitle = source.albumName,
        albumId = track.albumId,
        albumArtist = track.albumArtist,
        artworkPath = source.artworkPath,
        trackNumber = track.trackNumber,
        discNumber = track.discNumber,
        durationMs = track.durationMs,
        codec = track.codec,
        bitDepth = track.bitDepth,
        sampleRate = track.sampleRate,
        bitrate = track.bitrate,
        fileSize = size
    )
}

/**
 * Where playback starts once unplayable tracks are dropped. [kept] says, per requested track,
 * whether it resolved. The result indexes the kept tracks: the requested one if kept, else the
 * next kept one, else the previous. Null when nothing was kept.
 */
fun remapStartIndex(kept: List<Boolean>, startIndex: Int): Int? {
    if (kept.none { it }) return null
    val start = startIndex.coerceIn(0, kept.size - 1)
    val target = (start until kept.size).firstOrNull { kept[it] }
        ?: (start downTo 0).first { kept[it] }
    return kept.subList(0, target).count { it }
}
