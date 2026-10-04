package com.jpd.finsync.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jpd.finsync.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Turns queued Jellyfin IDs into playable file MediaItems at play time (overview rule 3). */
class TrackResolver(private val library: LibraryRepository) {

    /** One entry per ID, in order; null where the track can't be played. */
    suspend fun resolve(itemIds: List<String>): List<MediaItem?> = withContext(Dispatchers.IO) {
        itemIds.map { id ->
            resolveTrack(library.playableTrack(id), ::fileLengthOrNull)?.toMediaItem()
        }
    }

    /** The IDs that would resolve, for checking before anything is sent to the player. */
    suspend fun playableIds(itemIds: List<String>): Set<String> = withContext(Dispatchers.IO) {
        itemIds.filter { id ->
            resolveTrack(library.playableTrack(id), ::fileLengthOrNull) != null
        }.toSet()
    }

    private fun fileLengthOrNull(path: String): Long? =
        File(path).takeIf { it.isFile }?.length()
}

private fun ResolvedTrack.toMediaItem(): MediaItem {
    val extras = Bundle().apply {
        putLong(TrackExtras.FILE_SIZE, fileSize)
        albumId?.let { putString(TrackExtras.ALBUM_ID, it) }
        durationMs?.let { putLong(TrackExtras.DURATION_MS, it) }
        codec?.let { putString(TrackExtras.CODEC, it) }
        bitDepth?.let { putInt(TrackExtras.BIT_DEPTH, it) }
        sampleRate?.let { putInt(TrackExtras.SAMPLE_RATE, it) }
        bitrate?.let { putInt(TrackExtras.BITRATE, it) }
    }
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artists)
        .setAlbumTitle(albumTitle)
        .setAlbumArtist(albumArtist)
        .setTrackNumber(trackNumber)
        .setDiscNumber(discNumber)
        // The session's default bitmap loader reads file:// URIs (verification result 3).
        .setArtworkUri(artworkPath?.let { Uri.fromFile(File(it)) })
        .setExtras(extras)
        .build()
    return MediaItem.Builder()
        .setMediaId(itemId)
        .setUri(Uri.fromFile(File(path)))
        .setMediaMetadata(metadata)
        .build()
}
