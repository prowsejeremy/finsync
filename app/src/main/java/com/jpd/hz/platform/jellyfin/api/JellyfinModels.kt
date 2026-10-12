package com.jpd.hz.platform.jellyfin.api

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

// ── Quick Connect ───────────────────────────────────────────────────────────

/** A Quick Connect request (Jellyfin 12.1's `QuickConnectResult`); only these fields are read. */
data class QuickConnectResult(
    @SerializedName("Authenticated") val authenticated: Boolean,
    @SerializedName("Secret") val secret: String,
    @SerializedName("Code") val code: String
)

data class QuickConnectRequest(
    @SerializedName("Secret") val secret: String
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

/** One playlist and its entries, in server order (3b). */
data class ServerPlaylist(val playlist: MediaItem, val entries: List<MediaItem>)

/**
 * Everything sync and the catalogue refresh read from the server in one go: audio items,
 * playlists with their entries, and audiobooks (fetched from M2; none until then). A failed part
 * is recorded rather than fatal; the catalogue keeps its previous rows (spec "Order, cleanup and
 * failures").
 */
data class ServerCatalogue(
    val audio: List<MediaItem>,
    val playlists: List<ServerPlaylist>,
    val books: List<MediaItem> = emptyList(),
    /** The playlist list didn't load, so [playlists] is empty and every playlist keeps its rows. */
    val playlistsFailed: Boolean = false,
    /** Playlists whose entries didn't load; they're left out of [playlists] and keep their rows. */
    val failedPlaylistIds: Set<String> = emptySet(),
    /** The book list didn't load, so [books] is empty and every book keeps its rows. */
    val booksFailed: Boolean = false
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
    // With IDs, for the catalogue's link tables; Gson leaves them null when absent.
    @SerializedName("AlbumArtists") val albumArtists: List<NameId>? = null,
    @SerializedName("GenreItems") val genreItems: List<NameId>? = null,
    // Books (3b): sent only when Fields asks for People and Chapters (P0).
    @SerializedName("People") val people: List<PersonInfo>? = null,
    @SerializedName("Chapters") val chapters: List<ChapterInfo>? = null,
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
    @SerializedName("Channels") val channels: Int? = null,
    // Not confirmed for Jellyfin 12; Gson leaves it null when the server omits it.
    @SerializedName("BitDepth") val bitDepth: Int? = null
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

@Parcelize
data class PersonInfo(
    @SerializedName("Name") val name: String? = null,
    // A PersonKind name, such as "Author" or "Narrator" (Jellyfin 12.1.0 OpenAPI).
    @SerializedName("Type") val type: String? = null
) : Parcelable

@Parcelize
data class ChapterInfo(
    // Ticks: 10,000 per millisecond.
    @SerializedName("StartPositionTicks") val startPositionTicks: Long? = null,
    @SerializedName("Name") val name: String? = null
) : Parcelable
