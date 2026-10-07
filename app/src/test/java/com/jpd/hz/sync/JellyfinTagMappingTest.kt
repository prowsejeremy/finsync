package com.jpd.hz.sync

import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.NameId
import com.jpd.hz.model.PersonInfo
import com.jpd.hz.tags.TagFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The spec's mapping table, from server items to our fields. */
class JellyfinTagMappingTest {

    private val track = MediaItem(
        id = "t1",
        name = "One More Time",
        type = "Audio",
        albumArtist = "Daft Punk",
        album = "Discovery",
        trackNumber = 1,
        discNumber = 1,
        year = 2001,
        genres = listOf("House", "French House"),
        artists = listOf("Daft Punk", "Romanthony"),
        albumArtists = listOf(NameId("Daft Punk", "dp"))
    )

    private val book = MediaItem(
        id = "b1",
        name = "Project Hail Mary",
        type = "AudioBook",
        albumArtist = "Ray Porter",
        trackNumber = 4,
        discNumber = 1,
        year = 2021,
        genres = listOf("Science Fiction"),
        artists = listOf("Ray Porter"),
        people = listOf(PersonInfo("Andy Weir", "Author"), PersonInfo("Ray Porter", "Narrator"))
    )

    @Test
    fun `a music track writes every field of the table`() {
        assertEquals(
            mapOf(
                "TITLE" to "One More Time",
                "ARTIST" to "Daft Punk; Romanthony",
                "ALBUM" to "Discovery",
                "ALBUMARTIST" to "Daft Punk",
                "GENRE" to "House; French House",
                "DATE" to "2001",
                "TRACKNUMBER" to "1",
                "DISCNUMBER" to "1"
            ),
            JellyfinTagMapping.fieldsOf(track)
        )
    }

    @Test
    fun `album artists come from AlbumArtists, and missing values are left out`() {
        val sparse = track.copy(
            albumArtist = "Someone Else",
            albumArtists = listOf(NameId("Daft Punk", "dp"), NameId(" ", "blank")),
            genres = emptyList(),
            artists = null,
            year = null,
            trackNumber = null,
            discNumber = null,
            album = null
        )

        assertEquals(
            mapOf("TITLE" to "One More Time", "ALBUMARTIST" to "Daft Punk"),
            JellyfinTagMapping.fieldsOf(sparse)
        )
    }

    @Test
    fun `a book's authors fill both artist fields, its title is its album, and it has no numbers`() {
        assertEquals(
            mapOf(
                "TITLE" to "Project Hail Mary",
                "ARTIST" to "Andy Weir",
                "ALBUM" to "Project Hail Mary",
                "ALBUMARTIST" to "Andy Weir",
                "GENRE" to "Science Fiction",
                "DATE" to "2021"
            ),
            JellyfinTagMapping.fieldsOf(book)
        )
    }

    @Test
    fun `without an Author, a book's authors are its album artist, then its artists`() {
        val noAuthor = book.copy(people = listOf(PersonInfo("Ray Porter", "Narrator")))
        assertEquals("Ray Porter", JellyfinTagMapping.fieldsOf(noAuthor)["ARTIST"])

        val artistsOnly = noAuthor.copy(albumArtist = null, artists = listOf("Ann", "Bo"))
        assertEquals("Ann; Bo", JellyfinTagMapping.fieldsOf(artistsOnly)["ALBUMARTIST"])
    }

    @Test
    fun `an edit on the server changes the fingerprint, and the same data keeps it`() {
        val before = TagFingerprint.of(JellyfinTagMapping.fieldsOf(track))

        assertEquals(before, TagFingerprint.of(JellyfinTagMapping.fieldsOf(track.copy())))
        assertNotEquals(
            before,
            TagFingerprint.of(JellyfinTagMapping.fieldsOf(track.copy(genres = listOf("Disco"))))
        )
    }
}
