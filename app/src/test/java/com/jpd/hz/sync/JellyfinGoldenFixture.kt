package com.jpd.hz.sync

import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.MediaSource
import com.jpd.hz.model.NameId
import com.jpd.hz.model.PersonInfo
import com.jpd.hz.model.ServerCatalogue
import com.jpd.hz.model.ServerPlaylist

/**
 * A fixed Jellyfin catalogue for the golden tests (adapter harness spec, "Testing" 1): what
 * today's sync makes of it is recorded in the `golden` test resources, and the harness must make
 * the same.
 * It covers a multi-disc album, a compilation, names that need sanitising, a song with no album,
 * playlists with repeats, a missing entry and a shared name, and books with and without authors.
 */
object JellyfinGoldenFixture {

    private val daftPunk = NameId("Daft Punk", "dp")
    private val various = NameId("Various Artists", "va")
    private val acdc = NameId("AC/DC", "acdc")

    private fun track(
        id: String,
        name: String,
        album: String?,
        albumId: String?,
        albumArtist: String?,
        albumArtists: List<NameId>?,
        artists: List<String>,
        disc: Int?,
        number: Int?,
        path: String?,
        container: String? = null,
        genres: List<String>? = null,
        year: Int? = null
    ) = MediaItem(
        id = id,
        name = name,
        type = "Audio",
        albumArtist = albumArtist,
        album = album,
        albumId = albumId,
        trackNumber = number,
        discNumber = disc,
        runTimeTicks = 2_155_000_000L + number.orZero() * 10_000_000L,
        path = path,
        container = container,
        mediaSources = listOf(MediaSource(id = "src-$id", size = 7_000_000L)),
        year = year,
        genres = genres,
        artists = artists,
        albumArtists = albumArtists
    )

    private fun Int?.orZero(): Int = this ?: 0

    val audio: List<MediaItem> = listOf(
        track(
            "t1", "One More Time", "Discovery", "alb1", "Daft Punk", listOf(daftPunk),
            listOf("Daft Punk"), disc = 1, number = 1,
            path = "/srv/music/Daft Punk/Discovery/01 One More Time.flac",
            genres = listOf("House", "French House"), year = 2001
        ),
        track(
            "t2", "Aerodynamic", "Discovery", "alb1", "Daft Punk", listOf(daftPunk),
            listOf("Daft Punk"), disc = 1, number = 2,
            path = "/srv/music/Daft Punk/Discovery/02 Aerodynamic.flac", year = 2001
        ),
        track(
            "t3", "Digital Love", "Discovery", "alb1", "Daft Punk", listOf(daftPunk),
            listOf("Daft Punk", "Romanthony"), disc = 2, number = 1,
            path = "/srv/music/Daft Punk/Discovery/CD2/01 Digital Love.flac", year = 2001
        ),
        track(
            "c1", "Alpha Beta Gaga", "Late Night Tales", "alb2", "Various Artists",
            listOf(various), listOf("Air"), disc = null, number = 1,
            path = "/srv/music/Compilations/Late Night Tales/01 Alpha Beta Gaga.mp3"
        ),
        track(
            "c2", "Stay the Same", "Late Night Tales", "alb2", "Various Artists",
            listOf(various), listOf("Bonobo", "Andreya Triana"), disc = null, number = 2,
            path = "/srv/music/Compilations/Late Night Tales/02 Stay the Same.mp3",
            genres = listOf("Downtempo")
        ),
        track(
            "x1", "Thunder: Live?", "What's / Up?", "alb3", "AC/DC", listOf(acdc),
            listOf("AC/DC"), disc = 1, number = 7, path = null, container = "flac,ogg"
        ),
        track(
            "n1", "Loose Song", null, null, null, null,
            listOf("Solo"), disc = null, number = null,
            path = "/srv/music/Loose/Loose Song.m4a"
        )
    )

    private fun entry(id: String, type: String = "Audio") =
        MediaItem(id = id, name = "Entry $id", type = type)

    val playlists: List<ServerPlaylist> = listOf(
        ServerPlaylist(
            MediaItem(id = "p1", name = "Road Trip", type = "Playlist"),
            listOf(entry("c1"), entry("t1"), entry("gone"), entry("c1"))
        ),
        ServerPlaylist(
            MediaItem(id = "p2", name = "road trip", type = "Playlist"),
            listOf(entry("x1"), entry("n1"))
        ),
        ServerPlaylist(
            MediaItem(id = "p3", name = "Not chosen", type = "Playlist"),
            listOf(entry("t2"))
        ),
        ServerPlaylist(
            MediaItem(id = "p4", name = "Videos", type = "Playlist"),
            listOf(entry("v1", type = "Video"))
        )
    )

    val books: List<MediaItem> = listOf(
        MediaItem(
            id = "b1",
            name = "Dune",
            type = "AudioBook",
            path = "/srv/books/Frank Herbert/Dune/Dune.m4b",
            runTimeTicks = 760_000_000_000L,
            people = listOf(
                PersonInfo("Frank Herbert", "Author"),
                PersonInfo("Scott Brick", "Narrator")
            ),
            genres = listOf("Science Fiction"),
            year = 1965,
            mediaSources = listOf(MediaSource(id = "src-b1", size = 500_000_000L))
        ),
        MediaItem(
            id = "b2",
            name = "Untitled: Draft",
            type = "AudioBook",
            container = "m4b",
            runTimeTicks = 36_000_000_000L
        )
    )

    val catalogue = ServerCatalogue(audio = audio, playlists = playlists, books = books)

    /** A scenario: what's chosen, and the golden file its outputs must match. */
    data class Scenario(
        val file: String,
        val albumIds: Set<String>,
        val playlistIds: Set<String>,
        val bookIds: Set<String>
    )

    val scenarios = listOf(
        Scenario("golden/jellyfin-some.txt", setOf("alb1"), setOf("p1", "p2"), setOf("b1")),
        Scenario("golden/jellyfin-all.txt", setOf("all"), setOf("p1"), setOf("b1", "b2"))
    )
}
