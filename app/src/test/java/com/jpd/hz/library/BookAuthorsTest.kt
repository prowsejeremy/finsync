package com.jpd.hz.library

import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.PersonInfo
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * bookAuthorsOf, the list sync writes to a book's tags (T2). bookAuthorOf joins it, and the
 * book's folder is named from that, so the joined text must not change.
 */
class BookAuthorsTest {

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
            books.map(::bookAuthorsOf)
        )
    }

    @Test
    fun `bookAuthorOf is the list joined with a comma, or null`() {
        assertEquals(
            listOf("Ann, Bo", "Someone", "Ann, Bo", null, null),
            books.map(::bookAuthorOf)
        )
    }
}
