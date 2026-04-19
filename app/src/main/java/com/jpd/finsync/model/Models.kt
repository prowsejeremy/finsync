package com.jpd.finsync.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

// ── Auth ────────────────────────────────────────────────────────────────────

data class AuthenticateRequest(
    @SerializedName("Username") val username: String,
    @SerializedName("Pw") val password: String
)

data class AuthenticateResponse(
    @SerializedName("AccessToken") val accessToken: String,
    @SerializedName("ServerId") val serverId: String,
    @SerializedName("User") val user: JellyfinUser
)

data class JellyfinUser(
    @SerializedName("Id") val id: String,
    @SerializedName("Name") val name: String
)

// ── Server info ──────────────────────────────────────────────────────────────

data class ServerInfo(
    @SerializedName("ServerName") val serverName: String,
    @SerializedName("Version") val version: String,
    @SerializedName("Id") val id: String
)

// ── Music library items ──────────────────────────────────────────────────────

data class ItemsResponse(
    @SerializedName("Items") val items: List<MediaItem>,
    @SerializedName("TotalRecordCount") val totalRecordCount: Int
)

@Parcelize
data class MediaItem(
    @SerializedName("Id") val id: String,
    @SerializedName("Name") val name: String,
    @SerializedName("Type") val type: String,          // "Audio", "MusicAlbum", "MusicArtist", "MusicGenre"
    @SerializedName("AlbumArtist") val albumArtist: String? = null,
    @SerializedName("Album") val album: String? = null,
    @SerializedName("AlbumId") val albumId: String? = null,
    @SerializedName("IndexNumber") val trackNumber: Int? = null,
    @SerializedName("ParentIndexNumber") val discNumber: Int? = null,
    @SerializedName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerializedName("Path") val path: String? = null,
    @SerializedName("Container") val container: String? = null,   // "mp3", "flac", etc.
    @SerializedName("MediaSources") val mediaSources: List<MediaSource>? = null,
    @SerializedName("UserData") val userData: UserData? = null,
    @SerializedName("DateCreated") val dateCreated: String? = null,
    @SerializedName("DateModified") val dateModified: String? = null,
    @SerializedName("PremiereDate") val premiereDate: String? = null,
    @SerializedName("ProductionYear") val year: Int? = null,
    @SerializedName("Genres") val genres: List<String>? = null,
    @SerializedName("Artists") val artists: List<String>? = null,
    @SerializedName("ArtistItems") val artistItems: List<NameId>? = null,
    @SerializedName("ChildCount") val childCount: Int? = null
) : Parcelable {
    val durationMs: Long get() = (runTimeTicks ?: 0L) / 10_000
}

@Parcelize
data class MediaSource(
    @SerializedName("Id") val id: String,
    @SerializedName("Path") val path: String? = null,
    @SerializedName("Container") val container: String? = null,
    @SerializedName("Size") val size: Long? = null,
    @SerializedName("Bitrate") val bitrate: Int? = null,
    @SerializedName("MediaStreams") val mediaStreams: List<MediaStream>? = null
) : Parcelable

@Parcelize
data class MediaStream(
    @SerializedName("Type") val type: String,          // "Audio"
    @SerializedName("Codec") val codec: String? = null,
    @SerializedName("BitRate") val bitRate: Int? = null,
    @SerializedName("SampleRate") val sampleRate: Int? = null,
    @SerializedName("Channels") val channels: Int? = null
) : Parcelable

@Parcelize
data class UserData(
    @SerializedName("IsFavorite") val isFavorite: Boolean = false,
    @SerializedName("PlayCount") val playCount: Int = 0,
    @SerializedName("LastPlayedDate") val lastPlayedDate: String? = null
) : Parcelable

@Parcelize
data class NameId(
    @SerializedName("Name") val name: String,
    @SerializedName("Id") val id: String
) : Parcelable

// ── Sync state tracking ──────────────────────────────────────────────────────

data class SyncState(
    val totalItems: Int = 0,
    val downloadedItems: Int = 0,
    val currentTrack: String = "",
    val isRunning: Boolean = false,
    val errorMessage: String? = null,
    /** True when the user explicitly stopped the sync (distinct from an error). */
    val wasStopped: Boolean = false,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    /** True only on the terminal emission after a successful (non-stopped, non-error) sync run. */
    val syncComplete: Boolean = false
) {
    val progress: Int get() = if (totalItems > 0) (downloadedItems * 100) / totalItems else 0
}

// ── Album selection UI model ─────────────────────────────────────────────────

data class AlbumSelection(
    val item: MediaItem,
    var isSelected: Boolean,
    val isDownloaded: Boolean
)

// ── Persisted server config ──────────────────────────────────────────────────

data class ServerConfig(
    val serverUrl: String,
    val userId: String,
    val accessToken: String,
    val username: String,
    val serverId: String,
    val serverName: String
)
