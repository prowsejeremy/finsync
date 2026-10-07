package com.jpd.hz.tags

import org.junit.Assert.assertEquals
import org.junit.Test

class TagWritingTest {

    @Test
    fun writesEveryMusicFieldWithListsJoined() {
        val values = TagValues(
            title = "Get Lucky",
            artists = listOf("Daft Punk", "Pharrell Williams"),
            album = "Random Access Memories",
            albumArtists = listOf("Daft Punk"),
            genres = listOf("Electronic", "Disco"),
            year = 2013,
            trackNumber = 8,
            discNumber = 1
        )

        assertEquals(
            mapOf(
                "TITLE" to "Get Lucky",
                "ARTIST" to "Daft Punk; Pharrell Williams",
                "ALBUM" to "Random Access Memories",
                "ALBUMARTIST" to "Daft Punk",
                "GENRE" to "Electronic; Disco",
                "DATE" to "2013",
                "TRACKNUMBER" to "8",
                "DISCNUMBER" to "1"
            ),
            TagWriting.fieldsOf(values)
        )
    }

    @Test
    fun missingValuesAreLeftOutSoTheFileKeepsItsOwn() {
        val values = TagValues(title = "  ", album = null, genres = listOf(" ", ""))

        assertEquals(emptyMap<String, String>(), TagWriting.fieldsOf(values))
    }

    @Test
    fun listValuesAreTrimmedAndRepeatsDropped() {
        val values = TagValues(
            artists = listOf(" Daft Punk ", "", "Daft Punk", "daft  punk", "Pharrell Williams")
        )

        assertEquals(
            mapOf("ARTIST" to "Daft Punk; Pharrell Williams"),
            TagWriting.fieldsOf(values)
        )
    }

    @Test
    fun yearsAreFourDigitsAndOutOfRangeYearsAreLeftOut() {
        assertEquals("0999", TagWriting.fieldsOf(TagValues(year = 999))["DATE"])
        assertEquals(null, TagWriting.fieldsOf(TagValues(year = 0))["DATE"])
        assertEquals(null, TagWriting.fieldsOf(TagValues(year = 10_000))["DATE"])
    }

    @Test
    fun trackAndDiscNumbersArePlainAndNegativeOnesAreLeftOut() {
        val fields = TagWriting.fieldsOf(TagValues(trackNumber = 0, discNumber = -1))

        assertEquals(mapOf("TRACKNUMBER" to "0"), fields)
    }

    @Test
    fun aBookUsesItsTitleAsAlbumAndItsAuthorsAsBothArtistFields() {
        val values = TagValues.forBook(
            title = "Beholding",
            authors = listOf("Strahan Coleman"),
            genres = listOf("Christianity"),
            year = 2023
        )

        assertEquals(
            mapOf(
                "TITLE" to "Beholding",
                "ARTIST" to "Strahan Coleman",
                "ALBUM" to "Beholding",
                "ALBUMARTIST" to "Strahan Coleman",
                "GENRE" to "Christianity",
                "DATE" to "2023"
            ),
            TagWriting.fieldsOf(values)
        )
        assertEquals(TagField.BOOK.toSet(), TagWriting.fieldsOf(values).keys)
    }

    @Test
    fun theFieldTableMatchesTheSpec() {
        assertEquals(
            listOf(
                "TITLE", "ARTIST", "ALBUM", "ALBUMARTIST",
                "GENRE", "DATE", "TRACKNUMBER", "DISCNUMBER"
            ),
            TagField.MUSIC
        )
        assertEquals(TagField.MUSIC - listOf("TRACKNUMBER", "DISCNUMBER"), TagField.BOOK)
    }
}
