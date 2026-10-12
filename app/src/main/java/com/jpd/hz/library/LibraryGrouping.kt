package com.jpd.hz.library

import com.jpd.hz.library.db.ArtistCreditRow
import com.jpd.hz.library.db.GenreTrackRow

private const val DEFAULT_DISC = 1

private val ARTIST_ORDER: Comparator<ArtistSummary> =
    compareBy<ArtistSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.artistId }

private val GENRE_ORDER: Comparator<GenreSummary> =
    compareBy<GenreSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.genreId }

/** Group pages list albums newest first, then by name; undated albums go last. */
val GROUP_ALBUM_ORDER: Comparator<AlbumSummary> =
    compareBy<AlbumSummary> { it.year == null }
        .thenByDescending { it.year }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        .thenBy { it.albumId }

/**
 * All songs follows the album order above (a track without an album counts as undated), then
 * disc, track number and title.
 */
val GROUP_SONG_ORDER: Comparator<SongRow> =
    compareBy<SongRow> { it.albumYear == null }
        .thenByDescending { it.albumYear }
        .thenBy(nullsLast(String.CASE_INSENSITIVE_ORDER)) { it.albumName }
        .thenBy(nullsLast<String>()) { it.albumId }
        .thenBy { it.discNumber ?: DEFAULT_DISC }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        .thenBy { it.itemId }

/** Album Artists: artists with at least one album, A–Z ignoring case. */
fun albumArtistSummaries(credits: List<ArtistCreditRow>): List<ArtistSummary> =
    credits.groupBy { it.artistId }
        .map { (artistId, rows) ->
            ArtistSummary(
                artistId = artistId,
                name = rows.first().name,
                albumCount = rows.map { it.albumId }.distinct().size,
                photoPath = rows.first().photoPath
            )
        }
        .sortedWith(ARTIST_ORDER)

/** Genres with at least one track, A–Z ignoring case. */
fun genreSummaries(tags: List<GenreTrackRow>): List<GenreSummary> =
    tags.groupBy { it.genreId }
        .map { (genreId, rows) ->
            GenreSummary(
                genreId = genreId,
                name = rows.first().name,
                albumCount = rows.mapNotNull { it.albumId }.distinct().size,
                songCount = rows.map { it.trackId }.distinct().size
            )
        }
        .sortedWith(GENRE_ORDER)

/** All songs shows unless the page has exactly one album and the songs are just its tracks. */
fun showsAllSongs(albums: List<AlbumSummary>, songs: List<SongRow>): Boolean {
    val only = albums.singleOrNull() ?: return true
    val justThatAlbum = songs.size == only.downloadedTrackCount &&
        songs.all { it.albumId == only.albumId }
    return !justThatAlbum
}

/**
 * The page for an artist or genre from its albums and songs. Null when it has gone from the
 * library or has no songs, so the page says "Not downloaded".
 */
fun groupDetailOf(
    id: String,
    name: String?,
    photoPath: String?,
    albums: List<AlbumSummary>,
    songs: List<SongRow>
): GroupDetail? {
    if (name == null || songs.isEmpty()) return null
    return GroupDetail(
        id = id,
        name = name,
        photoPath = photoPath,
        albums = albums.sortedWith(GROUP_ALBUM_ORDER),
        songs = songs.sortedWith(GROUP_SONG_ORDER),
        showsAllSongs = showsAllSongs(albums, songs)
    )
}
