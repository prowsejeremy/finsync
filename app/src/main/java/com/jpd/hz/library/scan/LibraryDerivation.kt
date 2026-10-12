package com.jpd.hz.library.scan

import com.jpd.hz.library.db.LibraryAlbum
import com.jpd.hz.library.db.LibraryAlbumArtist
import com.jpd.hz.library.db.LibraryArtist
import com.jpd.hz.library.db.LibraryBook
import com.jpd.hz.library.db.LibraryGenre
import com.jpd.hz.library.db.LibraryPlaylist
import com.jpd.hz.library.db.LibraryPlaylistItem
import com.jpd.hz.library.db.LibraryTrack
import com.jpd.hz.library.db.LibraryTrackArtist
import com.jpd.hz.library.db.LibraryTrackGenre
import com.jpd.hz.tags.Normalising

private const val NAME_SEPARATOR = ", "
private const val DEFAULT_DISC = 1

/** The tables a pass derives from the files' rows (spec "Scanning", step 5). */
data class DerivedLibrary(
    val albums: List<LibraryAlbum>,
    val artists: List<LibraryArtist>,
    val albumArtists: List<LibraryAlbumArtist>,
    val trackArtists: List<LibraryTrackArtist>,
    val genres: List<LibraryGenre>,
    val trackGenres: List<LibraryTrackGenre>,
    val playlists: List<LibraryPlaylist>,
    val playlistItems: List<LibraryPlaylistItem>,
    /** The books with their covers set. */
    val books: List<LibraryBook>
)

/**
 * An album's or book's embedded cover, from the audio file at [path]: its name in the
 * embedded-art cache, or null when it has none.
 */
fun interface EmbeddedCovers {
    fun coverFor(id: String, path: String): String?
}

/**
 * Builds albums, artists, genres, their links, playlists and artwork from every track and book
 * row, their paths ([paths], by fileId), the walk's images and the parsed playlists (spec "The
 * Library folder format", "Our tags"). Paths stay relative to the Library folder (A1). Names show
 * their first spelling in path order. [embedded] is asked only for an album or book with no art
 * file beside it.
 */
fun deriveLibrary(
    tracks: List<LibraryTrack>,
    books: List<LibraryBook>,
    paths: Map<String, String>,
    playlists: List<Pair<String, ParsedPlaylist>>,
    images: Map<String, String>,
    embedded: EmbeddedCovers
): DerivedLibrary {
    fun pathOf(fileId: String): String = paths.getValue(fileId)
    val inPathOrder = tracks.sortedWith(compareBy(PATH_ORDER) { pathOf(it.trackId) })
    // An album's first track, whose folder gives its art: lowest disc, then track, then path.
    val albumOrder = compareBy<LibraryTrack> { it.discNumber ?: DEFAULT_DISC }
        .thenBy { it.trackNumber ?: Int.MAX_VALUE }
        .thenBy(PATH_ORDER) { pathOf(it.trackId) }
    val albums = albumsOf(inPathOrder, albumOrder, ::pathOf, images, embedded)
    val photos = LinkedHashMap<String, String>()
    for (album in albums) {
        val photo = ScanRules.artistPhoto(album.firstPath, images) ?: continue
        album.artistIds.firstOrNull()?.let { photos.putIfAbsent(it, photo) }
    }
    val artistNames = LinkedHashMap<String, String>()
    val genreNames = LinkedHashMap<String, String>()
    for (track in inPathOrder) {
        for (name in track.albumArtistNames + track.artistNames) {
            idOf(name)?.let { artistNames.putIfAbsent(it, name) }
        }
        for (name in track.genreNames) idOf(name)?.let { genreNames.putIfAbsent(it, name) }
    }
    val (playlistRows, itemRows) = playlistsOf(playlists, tracks, ::pathOf, images)
    return DerivedLibrary(
        albums = albums.map { it.row },
        artists = artistNames.map { (id, name) -> LibraryArtist(id, name, photos[id]) },
        albumArtists = albums.flatMap { album ->
            album.artistIds.mapIndexed { position, id ->
                LibraryAlbumArtist(album.row.albumId, id, position)
            }
        },
        trackArtists = inPathOrder.flatMap { track ->
            track.artistNames.mapNotNull(::idOf).distinct().mapIndexed { position, id ->
                LibraryTrackArtist(track.trackId, id, position)
            }
        },
        genres = genreNames.map { (id, name) -> LibraryGenre(id, name) },
        trackGenres = inPathOrder.flatMap { track ->
            track.genreNames.mapNotNull(::idOf).distinct()
                .map { LibraryTrackGenre(track.trackId, it) }
        },
        playlists = playlistRows,
        playlistItems = itemRows,
        books = books.map { book ->
            val path = pathOf(book.bookId)
            val cover = ScanRules.folderArt(path, images)
            book.copy(
                coverPath = cover,
                embeddedCover = if (cover == null) embedded.coverFor(book.bookId, path) else null
            )
        }
    )
}

private class DerivedAlbum(
    val row: LibraryAlbum,
    val artistIds: List<String>,
    val firstPath: String
)

// In path order of each album's first file, so "the first album in path order" comes first.
private fun albumsOf(
    inPathOrder: List<LibraryTrack>,
    albumOrder: Comparator<LibraryTrack>,
    pathOf: (String) -> String,
    images: Map<String, String>,
    embedded: EmbeddedCovers
): List<DerivedAlbum> =
    inPathOrder.filter { it.albumId != null }
        .groupBy { it.albumId.orEmpty() }
        .map { (albumId, members) ->
            val named = members.first()
            val firstPath = pathOf(members.minWith(albumOrder).trackId)
            val art = ScanRules.folderArt(firstPath, images)
            DerivedAlbum(
                row = LibraryAlbum(
                    albumId = albumId,
                    name = named.album.orEmpty(),
                    albumArtist = named.albumArtistNames.takeIf { it.isNotEmpty() }
                        ?.joinToString(NAME_SEPARATOR),
                    year = members.firstNotNullOfOrNull { it.year },
                    artworkPath = art,
                    embeddedArt = if (art == null) embedded.coverFor(albumId, firstPath) else null
                ),
                artistIds = named.albumArtistNames.mapNotNull(::idOf).distinct(),
                firstPath = firstPath
            )
        }

// Entries are paths, matched to the scanned songs' paths; ones that match none are left out, and
// the rest numbered from 0. Shared storage ignores case, so an entry may differ in case.
private fun playlistsOf(
    playlists: List<Pair<String, ParsedPlaylist>>,
    tracks: List<LibraryTrack>,
    pathOf: (String) -> String,
    images: Map<String, String>
): Pair<List<LibraryPlaylist>, List<LibraryPlaylistItem>> {
    val byPath = tracks.associate { pathOf(it.trackId) to it.trackId }
    val byLowerCasePath = byPath.mapKeys { it.key.lowercase() }
    val rows = ArrayList<LibraryPlaylist>()
    val items = ArrayList<LibraryPlaylistItem>()
    for ((path, parsed) in playlists) {
        rows.add(LibraryPlaylist(path, parsed.name, ScanRules.playlistCover(path, images)))
        parsed.entries
            .mapNotNull { entry -> byPath[entry] ?: byLowerCasePath[entry.lowercase()] }
            .forEachIndexed { position, trackId ->
                items.add(LibraryPlaylistItem(path, position, trackId))
            }
    }
    return rows to items
}

private fun idOf(name: String): String? = Normalising.normalise(name).takeIf { it.isNotEmpty() }
