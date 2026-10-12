package com.jpd.hz.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jpd.hz.library.db.FileKind
import com.jpd.hz.library.db.LibraryContents
import com.jpd.hz.library.db.LibraryDatabase
import com.jpd.hz.library.db.LibraryFile
import com.jpd.hz.library.db.LibraryTrack
import com.jpd.hz.library.scan.EmbeddedCovers
import com.jpd.hz.library.scan.ParsedPlaylist
import com.jpd.hz.library.scan.deriveLibrary
import com.jpd.hz.tags.Normalising
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

private const val LIBRARY = "/lib"
private const val ART_CACHE = "/cache"
private const val ID_PREFIX = "id-"
private const val ONE_MORE_TIME = "Music/Daft Punk/Discovery/01 One More Time.mp3"
private const val AERODYNAMIC = "Music/Daft Punk/Discovery/02 Aerodynamic.mp3"
private const val ONE_MORE_TIME_MS = 320_000L
private const val AERODYNAMIC_MS = 212_000L
private const val DISCOVERY_ART = "Music/Daft Punk/Discovery/folder.jpg"
private const val MIX = "Playlists/Mix.m3u8"
private const val MIX_COVER = "Playlists/Mix.jpg"

/** Runs the playlist queries and rules against an in-memory library database. */
@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryTest {

    private lateinit var database: LibraryDatabase
    private lateinit var repository: PlaylistRepository

    private val tracks = listOf(
        track(ONE_MORE_TIME, "One More Time", ONE_MORE_TIME_MS),
        track(AERODYNAMIC, "Aerodynamic", AERODYNAMIC_MS)
    )

    // Each track's path, by its file ID.
    private val paths = tracks.associate { it.trackId to it.trackId.removePrefix(ID_PREFIX) }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PlaylistRepository(
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

    private fun track(path: String, title: String, durationMs: Long): LibraryTrack {
        val albumArtists = listOf("Daft Punk")
        return LibraryTrack(
            trackId = idOf(path),
            title = title,
            artistNames = albumArtists,
            album = "Discovery",
            albumArtistNames = albumArtists,
            genreNames = emptyList(),
            year = 2001,
            discNumber = 1,
            trackNumber = null,
            durationMs = durationMs,
            codec = null,
            bitDepth = null,
            sampleRate = null,
            bitrate = null,
            size = 1,
            albumId = Normalising.albumIdOf(albumArtists, "Discovery")
        )
    }

    private fun images(vararg paths: String) = paths.associateBy { it.lowercase() }

    /** Writes what a scan of [tracks], the [playlists] and [images] would. */
    private suspend fun scan(
        playlists: List<Pair<String, ParsedPlaylist>>,
        images: Map<String, String> = images(DISCOVERY_ART)
    ) {
        val derived = deriveLibrary(
            tracks, emptyList(), paths, playlists, images, EmbeddedCovers { _, _ -> null }
        )
        val files = tracks.map { track ->
            LibraryFile(
                fileId = track.trackId,
                path = paths.getValue(track.trackId),
                size = 1,
                modifiedSec = 0,
                changedSec = 0,
                inode = 0,
                kind = FileKind.TRACK
            )
        }
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

    private fun playlist(path: String, name: String, vararg entries: String) =
        path to ParsedPlaylist(name, entries.toList())

    @Test
    fun `playlists with a song are listed A to Z`() = runBlocking {
        scan(
            listOf(
                playlist("Playlists/zeta.m3u8", "zeta", AERODYNAMIC),
                playlist(MIX, "mix", ONE_MORE_TIME, AERODYNAMIC),
                playlist("Playlists/Alpha.m3u8", "Alpha", ONE_MORE_TIME)
            )
        )

        val playlists = repository.playlists().first()

        assertEquals(listOf("Alpha", "mix", "zeta"), playlists.map { it.name })
        val mix = playlists[1]
        assertEquals(MIX, mix.playlistId)
        assertEquals(2, mix.songCount)
        assertEquals(listOf(ONE_MORE_TIME_MS, AERODYNAMIC_MS), mix.durationsMs)
    }

    @Test
    fun `a playlist with no song in the library is hidden`() = runBlocking {
        val gone = "Playlists/Gone.m3u8"
        scan(
            listOf(
                playlist(MIX, "Mix", ONE_MORE_TIME),
                playlist(gone, "Gone", "Music/Nobody/Nothing/01.mp3")
            )
        )

        assertEquals(listOf(MIX), repository.playlists().first().map { it.playlistId })
        assertNull(repository.playlist(gone).first())
    }

    @Test
    fun `a playlist's own cover comes before its first song's album art`() = runBlocking {
        val plain = "Playlists/Plain.m3u8"
        scan(
            listOf(
                playlist(MIX, "Mix", AERODYNAMIC),
                playlist(plain, "Plain", ONE_MORE_TIME)
            ),
            images(DISCOVERY_ART, MIX_COVER)
        )

        val covers = repository.playlists().first().associate { it.playlistId to it.coverPath }

        assertEquals("$LIBRARY/$MIX_COVER", covers[MIX])
        assertEquals("$LIBRARY/$DISCOVERY_ART", covers[plain])
        assertEquals("$LIBRARY/$MIX_COVER", repository.playlist(MIX).first()?.coverPath)
        assertEquals("$LIBRARY/$DISCOVERY_ART", repository.playlist(plain).first()?.coverPath)
    }

    @Test
    fun `a playlist's songs are in file order with a repeated song repeated`() = runBlocking {
        scan(listOf(playlist(MIX, "Mix", AERODYNAMIC, ONE_MORE_TIME, AERODYNAMIC)))

        val detail = repository.playlist(MIX).first()!!

        assertEquals("Mix", detail.name)
        assertEquals(
            listOf(AERODYNAMIC, ONE_MORE_TIME, AERODYNAMIC).map(::idOf),
            detail.songs.map { it.itemId }
        )
        assertEquals("Discovery", detail.songs.first().albumName)
    }

    @Test
    fun `an unknown playlist is null`() = runBlocking {
        scan(listOf(playlist(MIX, "Mix", ONE_MORE_TIME)))

        assertNull(repository.playlist("Playlists/Unknown.m3u8").first())
    }

    @Test
    fun `the playlist count leaves out playlists with no songs`() = runBlocking {
        scan(
            listOf(
                playlist(MIX, "Mix", ONE_MORE_TIME),
                playlist("Playlists/Other.m3u8", "Other", AERODYNAMIC),
                playlist("Playlists/Gone.m3u8", "Gone", "Music/Nobody/Nothing/01.mp3")
            )
        )

        assertEquals(2, repository.playlistCount().first())
    }
}
