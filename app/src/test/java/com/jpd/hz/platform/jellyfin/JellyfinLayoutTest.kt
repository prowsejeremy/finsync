package com.jpd.hz.platform.jellyfin

import com.jpd.hz.platform.jellyfin.api.MediaItem
import com.jpd.hz.platform.jellyfin.api.NameId
import com.jpd.hz.platform.jellyfin.api.PersonInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where Jellyfin's items land and how they're named: paths, labels, book authors and artist
 * photos (adapter harness spec, "The contract" and H9.5).
 */
class JellyfinLayoutTest {

    private val daftPunk = NameId("Daft Punk", "dp")

    private fun audio(
        id: String,
        albumId: String = "alb1",
        albumArtist: String? = "Daft Punk",
        albumArtists: List<NameId>? = listOf(daftPunk),
        artists: List<String>? = listOf("Daft Punk")
    ) = MediaItem(
        id = id,
        name = "Track $id",
        type = "Audio",
        albumArtist = albumArtist,
        album = "Discovery",
        albumId = albumId,
        path = "/srv/music/$id.flac",
        artists = artists,
        albumArtists = albumArtists
    )

    private fun book(
        albumArtist: String? = null,
        artists: List<String>? = null,
        people: List<PersonInfo>? = null
    ) = MediaItem(
        id = "b1",
        name = "Book",
        type = "AudioBook",
        albumArtist = albumArtist,
        artists = artists,
        people = people
    )

    // bookAuthorOf joins bookAuthorsOf, and a book's folder is named from it, so the joined text
    // must not change (T2).
    private val books = listOf(
        book(people = listOf(PersonInfo("Ann", "Author"), PersonInfo("Bo", "Author"))),
        book(albumArtist = "Someone", people = listOf(PersonInfo("Nell", "Narrator"))),
        book(albumArtist = " ", artists = listOf("Ann", " ", "Bo")),
        book(people = listOf(PersonInfo(" ", "Author"), PersonInfo(null, "Author"))),
        book()
    )

    @Test
    fun `authors are People of kind Author, else the album artist, else the artists`() {
        assertEquals(
            listOf(
                listOf("Ann", "Bo"),
                listOf("Someone"),
                listOf("Ann", "Bo"),
                emptyList(),
                emptyList()
            ),
            books.map(JellyfinLayout::bookAuthorsOf)
        )
    }

    @Test
    fun `bookAuthorOf is the list joined with a comma, or null`() {
        assertEquals(
            listOf("Ann, Bo", "Someone", "Ann, Bo", null, null),
            books.map(JellyfinLayout::bookAuthorOf)
        )
    }

    @Test
    fun `the author comes from People, then the album artist, then the artists`() {
        val people = listOf(
            PersonInfo("Stephen Fry", "Narrator"),
            PersonInfo("J. K. Rowling", "Author")
        )
        assertEquals(
            "J. K. Rowling",
            JellyfinLayout.bookAuthorOf(book(albumArtist = "Someone", people = people))
        )
        assertEquals(
            "Someone",
            JellyfinLayout.bookAuthorOf(book(albumArtist = "Someone", artists = listOf("X")))
        )
        assertEquals("Ann, Bo", JellyfinLayout.bookAuthorOf(book(artists = listOf("Ann", "Bo"))))
        assertNull(
            JellyfinLayout.bookAuthorOf(book(people = listOf(PersonInfo("Nell", "Narrator"))))
        )
    }

    @Test
    fun `each album artist's folder gets one photo, from its first album artist`() {
        val photos = JellyfinLayout.artistPhotosOf(
            listOf(
                audio("t1", albumArtists = listOf(daftPunk, NameId("Romanthony", "ro"))),
                audio("t2", albumId = "alb2", albumArtists = listOf(NameId("Other", "zz"))),
                audio("t3", albumArtist = "Air", albumArtists = listOf(NameId("Air", "air"))),
                audio("t4", albumArtist = null, albumArtists = listOf(NameId("X", "x"))),
                audio("t5", albumArtist = "Nobody", albumArtists = null)
            )
        )

        assertEquals(
            mapOf("Music/Daft Punk/artist.jpg" to "dp", "Music/Air/artist.jpg" to "air"),
            photos
        )
    }

    @Test
    fun `a song goes under its album artist and album, with the server's file name`() {
        assertEquals("Music/Daft Punk/Discovery/t1.flac", JellyfinLayout.pathOf(audio("t1")))

        val bare = MediaItem(id = "t9", name = "Untitled", type = "Audio", path = "/srv/x/t9.mp3")
        assertEquals("Music/Unknown Artist/Unknown Album/t9.mp3", JellyfinLayout.pathOf(bare))
    }

    @Test
    fun `a song the server gives no path is named from its number and title`() {
        val song = MediaItem(
            id = "t2",
            name = "Aerodynamic",
            type = "Audio",
            albumArtist = "Daft Punk",
            album = "Discovery",
            trackNumber = 3,
            container = "flac"
        )

        assertEquals("Music/Daft Punk/Discovery/03 Aerodynamic.flac", JellyfinLayout.pathOf(song))
    }

    @Test
    fun `a book goes under its author and title, or Unknown Author`() {
        val dune = MediaItem(
            id = "b2",
            name = "Dune",
            type = "AudioBook",
            path = "/srv/books/Dune.m4b",
            people = listOf(PersonInfo("Frank Herbert", "Author"))
        )

        assertTrue(JellyfinLayout.isBook(dune))
        assertFalse(JellyfinLayout.isBook(audio("t1")))
        assertEquals("Audiobooks/Frank Herbert/Dune/Dune.m4b", JellyfinLayout.pathOf(dune))
        assertEquals(
            "Audiobooks/Unknown Author/Dune/Dune.m4b",
            JellyfinLayout.pathOf(dune.copy(people = null))
        )
    }

    @Test
    fun `the label is the track artist and the title, or the title alone`() {
        assertEquals("Daft Punk - Track t1", JellyfinLayout.labelOf(audio("t1")))
        assertEquals(
            "Romanthony - Track t2",
            JellyfinLayout.labelOf(
                audio("t2", albumArtist = "Daft Punk", artists = listOf(" ", "Romanthony"))
            )
        )
        assertEquals(
            "Daft Punk - Track t3",
            JellyfinLayout.labelOf(audio("t3", artists = null))
        )
        assertEquals(
            "Track t4",
            JellyfinLayout.labelOf(audio("t4", albumArtist = null, artists = null))
        )
    }
}
