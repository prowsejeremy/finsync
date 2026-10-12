package com.jpd.hz.library.scan

import com.jpd.hz.library.db.LibraryAlbumArtist
import com.jpd.hz.library.db.LibraryBook
import com.jpd.hz.library.db.LibraryPlaylistItem
import com.jpd.hz.library.db.LibraryTrack
import com.jpd.hz.library.db.LibraryTrackArtist
import com.jpd.hz.library.db.LibraryTrackGenre
import com.jpd.hz.tags.Normalising
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryDerivationTest {

    private val embeddedCalls = ArrayList<Pair<String, String>>()
    private val embedded = EmbeddedCovers { id, path ->
        embeddedCalls.add(id to path)
        "cached-$id"
    }

    private fun track(
        path: String,
        album: String? = "Album",
        albumArtists: List<String> = listOf("Artist"),
        artists: List<String> = albumArtists,
        genres: List<String> = emptyList(),
        disc: Int? = null,
        number: Int? = null,
        year: Int? = null
    ) = LibraryTrack(
        trackId = path,
        title = path.substringAfterLast('/'),
        artistNames = artists,
        album = album,
        albumArtistNames = albumArtists,
        genreNames = genres,
        year = year,
        discNumber = disc,
        trackNumber = number,
        durationMs = null,
        codec = null,
        bitDepth = null,
        sampleRate = null,
        bitrate = null,
        size = 1,
        albumId = Normalising.albumIdOf(albumArtists, album)
    )

    private fun book(path: String) = LibraryBook(
        bookId = path, title = "Book", author = null, durationMs = null, codec = null,
        bitDepth = null, sampleRate = null, bitrate = null, size = 1, coverPath = null,
        embeddedCover = null
    )

    private fun images(vararg paths: String) = paths.associateBy { it.lowercase() }

    private fun derive(
        tracks: List<LibraryTrack>,
        images: Map<String, String> = emptyMap(),
        books: List<LibraryBook> = emptyList(),
        playlists: List<Pair<String, ParsedPlaylist>> = emptyList()
    ) = deriveLibrary(tracks, books, pathsOf(tracks, books), playlists, images, embedded)

    // Most tests name each file's ID after its path; a separate test keeps them apart.
    private fun pathsOf(tracks: List<LibraryTrack>, books: List<LibraryBook>) =
        (tracks.map { it.trackId } + books.map { it.bookId }).associateWith { it }

    @Test
    fun anAlbumAcrossDiscFoldersIsOneAlbumWithArtFromItsFirstTrack() {
        val derived = derive(
            listOf(
                track("Music/A/Album/CD2/01.mp3", disc = 2, number = 1, year = 2001),
                track("Music/A/Album/CD1/02.mp3", disc = 1, number = 2),
                track("Music/A/Album/CD1/01.mp3", disc = 1, number = 1)
            ),
            images("Music/A/Album/CD1/folder.jpg", "Music/A/Album/CD2/folder.jpg")
        )

        val album = derived.albums.single()
        assertEquals("Music/A/Album/CD1/folder.jpg", album.artworkPath)
        assertNull(album.embeddedArt)
        assertEquals("Album", album.name)
        assertEquals("Artist", album.albumArtist)
        assertEquals(2001, album.year)
        assertEquals(emptyList<Pair<String, String>>(), embeddedCalls)
    }

    @Test
    fun theSameAlbumNameByOtherAlbumArtistsIsAnotherAlbum() {
        val derived = derive(
            listOf(
                track("A/Greatest Hits/01.mp3", "Greatest Hits", albumArtists = listOf("A")),
                track("B/Greatest Hits/01.mp3", "Greatest Hits", albumArtists = listOf("B"))
            )
        )

        assertEquals(2, derived.albums.size)
    }

    @Test
    fun withoutAnArtFileTheFirstTracksEmbeddedCoverIsUsed() {
        val derived = derive(
            listOf(track("X/02.mp3", number = 2), track("X/01.mp3", number = 1)),
            books = listOf(book("Audiobooks/B/b.m4b"), book("Audiobooks/C/c.m4b")),
            images = images("Audiobooks/C/cover.png")
        )

        val albumId = derived.albums.single().albumId
        assertNull(derived.albums.single().artworkPath)
        assertEquals("cached-$albumId", derived.albums.single().embeddedArt)
        assertEquals("cached-Audiobooks/B/b.m4b", derived.books[0].embeddedCover)
        assertNull(derived.books[0].coverPath)
        assertEquals("Audiobooks/C/cover.png", derived.books[1].coverPath)
        assertNull(derived.books[1].embeddedCover)
        assertEquals(
            listOf(albumId to "X/01.mp3", "Audiobooks/B/b.m4b" to "Audiobooks/B/b.m4b"),
            embeddedCalls
        )
    }

    @Test
    fun namesShowTheirFirstSpellingInPathOrder() {
        val derived = derive(
            listOf(
                track("b/1.mp3", album = "One", albumArtists = listOf("Daft Punk"),
                    genres = listOf("House")),
                track("a/1.mp3", album = "Two", albumArtists = listOf("daft punk"),
                    artists = listOf("daft punk", "Romanthony"), genres = listOf("house", "Disco"))
            )
        )

        assertEquals(
            listOf("daft punk" to "daft punk", "romanthony" to "Romanthony"),
            derived.artists.map { it.artistId to it.name }
        )
        assertEquals(
            listOf("house" to "house", "disco" to "Disco"),
            derived.genres.map { it.genreId to it.name }
        )
        assertEquals(
            listOf(
                LibraryTrackGenre("a/1.mp3", "house"),
                LibraryTrackGenre("a/1.mp3", "disco"),
                LibraryTrackGenre("b/1.mp3", "house")
            ),
            derived.trackGenres
        )
        assertEquals(
            listOf(
                LibraryTrackArtist("a/1.mp3", "daft punk", 0),
                LibraryTrackArtist("a/1.mp3", "romanthony", 1),
                LibraryTrackArtist("b/1.mp3", "daft punk", 0)
            ),
            derived.trackArtists
        )
    }

    @Test
    fun albumArtistsKeepTheirOrderAndShowJoined() {
        val derived = derive(
            listOf(track("X/1.mp3", albumArtists = listOf("Simon", "Garfunkel")))
        )

        val album = derived.albums.single()
        assertEquals("Simon, Garfunkel", album.albumArtist)
        assertEquals(
            listOf(
                LibraryAlbumArtist(album.albumId, "simon", 0),
                LibraryAlbumArtist(album.albumId, "garfunkel", 1)
            ),
            derived.albumArtists
        )
    }

    @Test
    fun theFirstAlbumOfferingAPhotoGivesItsFirstAlbumArtistOne() {
        val daftPunk = listOf("Daft Punk")
        val derived = derive(
            listOf(
                track("Music/Daft Punk/Discovery/01.mp3", "Discovery", albumArtists = daftPunk),
                track("Other/Daft Punk/Homework/01.mp3", "Homework", albumArtists = daftPunk),
                track("Loose/01.mp3", album = "Loose", albumArtists = listOf("Solo"))
            ),
            images("Music/Daft Punk/artist.jpg", "Other/Daft Punk/artist.png", "artist.jpg")
        )

        val photos = derived.artists.associate { it.artistId to it.photoPath }
        assertEquals("Music/Daft Punk/artist.jpg", photos["daft punk"])
        assertNull(photos["solo"])
    }

    @Test
    fun aTrackWithNoAlbumHasNoAlbumButKeepsItsArtists() {
        val derived = derive(listOf(track("loose.mp3", album = null, albumArtists = listOf("A"))))

        assertEquals(emptyList<Any>(), derived.albums)
        assertEquals(listOf("a"), derived.artists.map { it.artistId })
        assertEquals(listOf(LibraryTrackArtist("loose.mp3", "a", 0)), derived.trackArtists)
    }

    @Test
    fun playlistsKeepOnlyScannedSongsNumberedFromZero() {
        val derived = derive(
            listOf(track("kurage/Music/A/01.mp3"), track("kurage/Music/A/02.mp3")),
            images = images("kurage/Playlists/Mix.jpg"),
            books = listOf(book("kurage/Audiobooks/b.m4b")),
            playlists = listOf(
                "kurage/Playlists/Mix.m3u8" to ParsedPlaylist(
                    "Mix",
                    listOf(
                        "kurage/Music/A/02.mp3",
                        "kurage/Music/A/missing.mp3",
                        "kurage/audiobooks/b.m4b",
                        "KURAGE/music/a/01.MP3",
                        "kurage/Music/A/02.mp3"
                    )
                ),
                "Empty.m3u" to ParsedPlaylist("Empty", listOf("nothing.mp3"))
            )
        )

        assertEquals(
            listOf(
                LibraryPlaylistItem("kurage/Playlists/Mix.m3u8", 0, "kurage/Music/A/02.mp3"),
                LibraryPlaylistItem("kurage/Playlists/Mix.m3u8", 1, "kurage/Music/A/01.mp3"),
                LibraryPlaylistItem("kurage/Playlists/Mix.m3u8", 2, "kurage/Music/A/02.mp3")
            ),
            derived.playlistItems
        )
        assertEquals(listOf("Mix", "Empty"), derived.playlists.map { it.name })
        assertEquals("kurage/Playlists/Mix.jpg", derived.playlists[0].coverPath)
        assertNull(derived.playlists[1].coverPath)
    }

    @Test
    fun idsAndPathsAreKeptApart() {
        val one = track("ignored").copy(trackId = "id-1")
        val two = track("ignored").copy(trackId = "id-2", trackNumber = 2)
        val paths = mapOf("id-1" to "Music/A/Album/01.mp3", "id-2" to "Music/A/Album/02.mp3")

        val derived = deriveLibrary(
            listOf(two, one),
            emptyList(),
            paths,
            listOf("Mix.m3u8" to ParsedPlaylist("Mix", listOf("Music/A/Album/02.mp3"))),
            images("Music/A/Album/folder.jpg"),
            embedded
        )

        assertEquals("Music/A/Album/folder.jpg", derived.albums.single().artworkPath)
        assertEquals(listOf("id-1", "id-2"), derived.trackArtists.map { it.trackId })
        assertEquals(listOf(LibraryPlaylistItem("Mix.m3u8", 0, "id-2")), derived.playlistItems)
    }
}
