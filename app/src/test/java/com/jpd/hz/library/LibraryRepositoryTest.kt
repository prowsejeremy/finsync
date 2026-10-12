package com.jpd.hz.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.library.db.FileKind
import com.jpd.hz.library.db.LibraryBook
import com.jpd.hz.library.db.LibraryContents
import com.jpd.hz.library.db.LibraryDatabase
import com.jpd.hz.library.db.LibraryFile
import com.jpd.hz.library.db.LibraryTrack
import com.jpd.hz.library.scan.EmbeddedCovers
import com.jpd.hz.library.scan.deriveLibrary
import com.jpd.hz.tags.Normalising
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private const val LIBRARY = "/lib"
private const val ART_CACHE = "/cache"
private const val ID_PREFIX = "id-"
private const val EMBEDDED_ART = "f00d.jpg"
private const val KURT = "Kurt Vile"
private const val COURTNEY = "Courtney Barnett"
private const val KIM = "Kim Gordon"
private const val PLAYABLE_TRACK_COUNT = 1_000

/** Runs the repository's SQL and Kotlin rules against an in-memory library database. */
@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {

    private lateinit var database: LibraryDatabase
    private lateinit var repository: LibraryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = LibraryRepository(
            database.libraryDao(),
            LibraryFiles({ File(LIBRARY) }, File(ART_CACHE))
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // A file's ID is its path with a prefix, so no test can pass by mistaking one for the other.
    private fun idOf(path: String) = ID_PREFIX + path

    private fun track(
        path: String,
        title: String = path.substringAfterLast('/').substringBeforeLast('.'),
        album: String? = "Album",
        albumArtists: List<String> = listOf(KURT),
        artists: List<String> = albumArtists,
        genres: List<String> = emptyList(),
        disc: Int? = null,
        number: Int? = null,
        year: Int? = null,
        id: String = idOf(path)
    ) = LibraryTrack(
        trackId = id,
        title = title,
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
        bookId = idOf(path), title = "Book", author = null, durationMs = null, codec = null,
        bitDepth = null, sampleRate = null, bitrate = null, size = 1, coverPath = null,
        embeddedCover = null
    )

    /** Each file's path, by its ID, for files named with [idOf]. */
    private fun pathsOf(tracks: List<LibraryTrack>, books: List<LibraryBook>) =
        (tracks.map { it.trackId } + books.map { it.bookId })
            .associateWith { it.removePrefix(ID_PREFIX) }

    private fun fileOf(fileId: String, paths: Map<String, String>, kind: FileKind) = LibraryFile(
        fileId = fileId,
        path = paths.getValue(fileId),
        size = 1,
        modifiedSec = 0,
        changedSec = 0,
        inode = 0,
        kind = kind
    )

    /**
     * Writes what a scan of [tracks], [books] and [images] would, finding each file at its entry
     * in [paths] and each embedded cover through [embedded].
     */
    private suspend fun scan(
        tracks: List<LibraryTrack>,
        books: List<LibraryBook> = emptyList(),
        images: Map<String, String> = emptyMap(),
        paths: Map<String, String> = pathsOf(tracks, books),
        embedded: EmbeddedCovers = EmbeddedCovers { _, _ -> null }
    ) {
        val derived = deriveLibrary(tracks, books, paths, emptyList(), images, embedded)
        val files = tracks.map { fileOf(it.trackId, paths, FileKind.TRACK) } +
            books.map { fileOf(it.bookId, paths, FileKind.BOOK) }
        database.scanDao().replaceLibrary(
            LibraryContents(
                files = files,
                tracks = tracks,
                albums = derived.albums,
                artists = derived.artists,
                albumArtists = derived.albumArtists,
                trackArtists = derived.trackArtists,
                genres = derived.genres,
                trackGenres = derived.trackGenres,
                playlists = derived.playlists,
                playlistItems = derived.playlistItems,
                books = derived.books,
                chapters = emptyList()
            )
        )
    }

    private fun artistId(name: String) = Normalising.normalise(name)

    private fun albumId(albumArtists: List<String>, album: String): String =
        checkNotNull(Normalising.albumIdOf(albumArtists, album))

    @Test
    fun `album artists are listed A to Z with their album counts`() = runBlocking {
        scan(
            listOf(
                track("Music/Kurt Vile/Wakin/01.mp3", album = "Wakin"),
                track("Music/Kurt Vile/Smoke Ring/01.mp3", album = "Smoke Ring"),
                track("Music/beck/Odelay/01.mp3", album = "Odelay", albumArtists = listOf("beck"))
            )
        )

        val artists = repository.albumArtists().first()

        assertEquals(listOf("beck", KURT), artists.map { it.name })
        assertEquals(listOf(1, 2), artists.map { it.albumCount })
    }

    @Test
    fun `a joint album appears under both its album artists`() = runBlocking {
        val joint = listOf(KURT, COURTNEY)
        val path = "Music/Joint/Lotta Sea Lice/01.mp3"
        scan(listOf(track(path, album = "Lotta Sea Lice", albumArtists = joint)))
        val jointId = albumId(joint, "Lotta Sea Lice")

        val artists = repository.albumArtists().first()

        assertEquals(listOf(COURTNEY, KURT), artists.map { it.name })
        assertEquals(listOf(1, 1), artists.map { it.albumCount })
        for (name in joint) {
            val page = repository.artist(artistId(name)).first()!!
            assertEquals(listOf(jointId), page.albums.map { it.albumId })
        }
    }

    @Test
    fun `an artist's songs include a featured track on another album`() = runBlocking {
        val own = track("Music/Kurt Vile/Wakin/01.mp3", album = "Wakin", year = 2013)
        val featured = track(
            "Music/Kim Gordon/Collaborations/01.mp3",
            album = "Collaborations",
            albumArtists = listOf(KIM),
            artists = listOf(KIM, KURT),
            year = 2019
        )
        val unrelated = track(
            "Music/Kim Gordon/Collaborations/02.mp3",
            album = "Collaborations",
            albumArtists = listOf(KIM),
            year = 2019
        )
        scan(listOf(own, featured, unrelated))

        val page = repository.artist(artistId(KURT)).first()!!

        assertEquals(KURT, page.name)
        assertEquals(listOf(albumId(listOf(KURT), "Wakin")), page.albums.map { it.albumId })
        assertEquals(listOf(featured.trackId, own.trackId), page.songs.map { it.itemId })
        assertTrue(page.showsAllSongs)
    }

    @Test
    fun `a genre page lists the albums and songs in the genre`() = runBlocking {
        val rockOnX = track("Music/A/X/01.mp3", album = "X", genres = listOf("Rock"), year = 2020)
        val jazzOnX = track("Music/A/X/02.mp3", album = "X", genres = listOf("Jazz"), year = 2020)
        val rockOnY = track("Music/A/Y/01.mp3", album = "Y", genres = listOf("Rock"), year = 2010)
        val popOnZ = track("Music/A/Z/01.mp3", album = "Z", genres = listOf("Pop"), year = 2000)
        scan(listOf(rockOnX, jazzOnX, rockOnY, popOnZ))

        val page = repository.genre(artistId("Rock")).first()!!

        assertEquals("Rock", page.name)
        assertNull(page.photoPath)
        assertEquals(listOf("X", "Y"), page.albums.map { it.name })
        assertEquals(listOf(rockOnX.trackId, rockOnY.trackId), page.songs.map { it.itemId })
    }

    @Test
    fun `a group page lists albums newest first and undated albums last`() = runBlocking {
        scan(
            listOf(
                track("Music/Kurt Vile/Old/01.mp3", album = "Old", year = 1999),
                track("Music/Kurt Vile/Undated/01.mp3", album = "Undated"),
                track("Music/Kurt Vile/New/01.mp3", album = "New", year = 2020),
                track("Music/Kurt Vile/Mid/01.mp3", album = "Mid", year = 2010)
            )
        )

        val page = repository.artist(artistId(KURT)).first()!!

        assertEquals(listOf("New", "Mid", "Old", "Undated"), page.albums.map { it.name })
    }

    @Test
    fun `songs are A to Z ignoring case`() = runBlocking {
        scan(
            listOf(
                track("Music/A/X/01.mp3", title = "cherry"),
                track("Music/A/X/02.mp3", title = "Apple"),
                track("Music/Loose/03.mp3", title = "banana", album = null)
            )
        )

        val songs = repository.songs().first()

        assertEquals(listOf("Apple", "banana", "cherry"), songs.map { it.title })
        assertNull(songs[1].albumId)
    }

    @Test
    fun `home counts albums, album artists, genres and songs`() = runBlocking {
        scan(
            listOf(
                track("Music/Kurt Vile/Wakin/01.mp3", album = "Wakin", genres = listOf("Rock")),
                track(
                    "Music/Kurt Vile/Wakin/02.mp3",
                    album = "Wakin",
                    genres = listOf("Rock", "Folk")
                ),
                track(
                    "Music/Kim Gordon/No Home Record/01.mp3",
                    album = "No Home Record",
                    albumArtists = listOf(KIM),
                    genres = listOf("Noise")
                ),
                // No album, so its album artist has no album to list.
                track("Music/Loose/01.mp3", album = null, albumArtists = listOf("Lucy Harrow"))
            )
        )

        assertEquals(2, repository.albumCount().first())
        assertEquals(2, repository.albumArtistCount().first())
        assertEquals(3, repository.genreCount().first())
        assertEquals(4, repository.songCount().first())
    }

    @Test
    fun `the library is empty only with no tracks and no books`() = runBlocking {
        assertTrue(repository.isLibraryEmpty().first())

        scan(emptyList(), books = listOf(book("Audiobooks/Hurry/Hurry.m4b")))
        assertFalse(repository.isLibraryEmpty().first())

        scan(listOf(track("Music/A/X/01.mp3")))
        assertFalse(repository.isLibraryEmpty().first())
    }

    @Test
    fun `an album lists its tracks in disc and number order`() = runBlocking {
        scan(
            listOf(
                track("Music/A/Album/CD2/01.mp3", disc = 2, number = 1),
                track("Music/A/Album/CD1/03.mp3", number = 3),
                track("Music/A/Album/CD1/02.mp3", disc = 1, number = 2),
                track("Music/A/Album/CD1/01.mp3", disc = 1, number = 1)
            )
        )

        val album = repository.album(albumId(listOf(KURT), "Album")).first()!!

        assertEquals("Album", album.name)
        assertEquals(KURT, album.albumArtist)
        assertEquals(
            listOf(
                "Music/A/Album/CD1/01.mp3",
                "Music/A/Album/CD1/02.mp3",
                "Music/A/Album/CD1/03.mp3",
                "Music/A/Album/CD2/01.mp3"
            ).map(::idOf),
            album.tracks.map { it.trackId }
        )
    }

    @Test
    fun `an unknown album is null`() = runBlocking {
        scan(listOf(track("Music/A/X/01.mp3")))

        assertNull(repository.album("nobody\u001Fnothing").first())
    }

    @Test
    fun `playable tracks keep the order asked across a thousand IDs`() = runBlocking {
        val tracks = (1..PLAYABLE_TRACK_COUNT).map { track("Music/A/X/%04d.mp3".format(it)) }
        scan(tracks)
        val missing = idOf("Music/A/X/missing.mp3")
        val asked = tracks.map { it.trackId }.reversed().toMutableList()
        asked.add(PLAYABLE_TRACK_COUNT / 2, missing)

        val sources = repository.playableTracks(asked)

        val expected = asked.map { id -> id.takeUnless { it == missing } }
        assertEquals(expected, sources.map { it?.track?.trackId })
        assertNull(sources[PLAYABLE_TRACK_COUNT / 2])
    }

    @Test
    fun `a playable track has its file, album, first album artist and folder art`() =
        runBlocking {
            val joint = listOf(KURT, COURTNEY)
            val path = "Music/Joint/Lotta Sea Lice/01 Over Everything.mp3"
            scan(
                listOf(track(path, album = "Lotta Sea Lice", albumArtists = joint)),
                images = mapOf(
                    "music/joint/lotta sea lice/folder.jpg" to
                        "Music/Joint/Lotta Sea Lice/folder.jpg"
                )
            )

            val source = repository.playableTracks(listOf(idOf(path))).single()!!

            assertEquals("$LIBRARY/$path", source.localPath)
            assertEquals("Lotta Sea Lice", source.albumName)
            assertEquals("$KURT, $COURTNEY", source.albumArtist)
            assertEquals(artistId(KURT), source.albumArtistId)
            assertEquals("$LIBRARY/Music/Joint/Lotta Sea Lice/folder.jpg", source.artworkPath)
        }

    @Test
    fun `a playable track is found by its file ID, with its album's embedded cover`() =
        runBlocking {
            val path = "Music/Kim Gordon/No Home Record/01 Sketch Artist.mp3"
            val fileId = "file-7"
            val noHomeRecord = track(
                path,
                album = "No Home Record",
                albumArtists = listOf(KIM),
                id = fileId
            )
            scan(
                listOf(noHomeRecord),
                paths = mapOf(fileId to path),
                embedded = EmbeddedCovers { _, _ -> EMBEDDED_ART }
            )

            val source = repository.playableTracks(listOf(fileId)).single()!!

            assertEquals(fileId, source.track.trackId)
            assertEquals("$LIBRARY/$path", source.localPath)
            assertEquals("$ART_CACHE/$EMBEDDED_ART", source.artworkPath)
            assertNull(repository.playableTracks(listOf(path)).single())
        }
}
