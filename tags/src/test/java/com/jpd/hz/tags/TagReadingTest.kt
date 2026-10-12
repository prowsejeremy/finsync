package com.jpd.hz.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagReadingTest {

    @Test
    fun nativeValuesAndSemicolonsBothSplit() {
        assertEquals(
            listOf("Rock", "Pop", "Jazz"),
            TagReading.splitValues(listOf("Rock; Pop", "Jazz"))
        )
    }

    @Test
    fun splitValuesAreTrimmedAndBlanksAndRepeatsDropped() {
        assertEquals(
            listOf("Rock", "Pop"),
            TagReading.splitValues(listOf(" Rock ;; Pop;", "Rock", "  ", "rock", "POP "))
        )
    }

    @Test
    fun slashesCommasAndFeatNeverSplit() {
        assertEquals(
            listOf("AC/DC", "Crosby, Stills & Nash", "Calvin Harris feat. Rihanna"),
            TagReading.splitValues(
                listOf("AC/DC; Crosby, Stills & Nash", "Calvin Harris feat. Rihanna")
            )
        )
    }

    @Test
    fun aMissingFieldSplitsToNothing() {
        assertEquals(emptyList<String>(), TagReading.splitValues(null))
    }

    @Test
    fun readsATaggedTrack() {
        val fields = mapOf(
            "TITLE" to listOf("Get Lucky"),
            "ARTIST" to listOf("Daft Punk; Pharrell Williams"),
            "ALBUM" to listOf("Random Access Memories"),
            "ALBUMARTIST" to listOf("Daft Punk"),
            "GENRE" to listOf("Electronic", "Disco"),
            "DATE" to listOf("2013-05-17"),
            "TRACKNUMBER" to listOf("8/13"),
            "DISCNUMBER" to listOf("1/1")
        )

        assertEquals(
            TrackTags(
                title = "Get Lucky",
                artists = listOf("Daft Punk", "Pharrell Williams"),
                album = "Random Access Memories",
                albumArtists = listOf("Daft Punk"),
                genres = listOf("Electronic", "Disco"),
                year = 2013,
                trackNumber = 8,
                discNumber = 1
            ),
            TagReading.track(fields, "08 Get Lucky.flac")
        )
    }

    @Test
    fun anUntaggedTrackIsTitledFromItsFileName() {
        assertEquals(
            TrackTags(
                title = "08 Get Lucky",
                artists = emptyList(),
                album = null,
                albumArtists = emptyList(),
                genres = emptyList(),
                year = null,
                trackNumber = null,
                discNumber = null
            ),
            TagReading.track(emptyMap(), "08 Get Lucky.flac")
        )
    }

    @Test
    fun albumArtistsFallBackToTheFirstTrackArtist() {
        val fields = mapOf("ARTIST" to listOf("Daft Punk; Pharrell Williams"))

        assertEquals(listOf("Daft Punk"), TagReading.track(fields, "a.mp3").albumArtists)
    }

    @Test
    fun titlesAndAlbumsAreNotSplitOnSemicolons() {
        val fields = mapOf(
            "TITLE" to listOf(" Hello; Goodbye "),
            "ALBUM" to listOf("", "Love; Hate")
        )

        val track = TagReading.track(fields, "a.mp3")

        assertEquals("Hello; Goodbye", track.title)
        assertEquals("Love; Hate", track.album)
    }

    @Test
    fun aBlankTitleFallsBackToTheFileName() {
        val fields = mapOf("TITLE" to listOf("   "))

        assertEquals("One More Time", TagReading.track(fields, "One More Time.mp3").title)
    }

    @Test
    fun theYearIsTheFirstRunOfFourDigits() {
        assertEquals(2013, TagReading.yearOf("2013"))
        assertEquals(2013, TagReading.yearOf("2013-05-17"))
        assertEquals(2013, TagReading.yearOf("℗ 2013 Columbia"))
        assertEquals(2013, TagReading.yearOf("17/05/2013"))
        assertNull(TagReading.yearOf("'13"))
        assertNull(TagReading.yearOf("٢٠١٣"))
        assertNull(TagReading.yearOf(null))
    }

    @Test
    fun trackAndDiscReadTheLeadingWholeNumber() {
        assertEquals(3, TagReading.leadingNumber("3/12"))
        assertEquals(3, TagReading.leadingNumber(" 03 "))
        assertEquals(0, TagReading.leadingNumber("0"))
        assertNull(TagReading.leadingNumber("A1"))
        assertNull(TagReading.leadingNumber("99999999999"))
        assertNull(TagReading.leadingNumber(null))
    }

    @Test
    fun aBookPrefersAlbumThenTitleThenFileName() {
        assertEquals(
            "Beholding (Unabridged)",
            TagReading.book(
                mapOf("ALBUM" to listOf("Beholding (Unabridged)"), "TITLE" to listOf("Beholding")),
                "b.m4b"
            ).title
        )
        assertEquals(
            "Beholding",
            TagReading.book(mapOf("TITLE" to listOf("Beholding")), "b.m4b").title
        )
        assertEquals("Beholding", TagReading.book(emptyMap(), "Beholding.m4b").title)
    }

    @Test
    fun aBooksAuthorsAreItsAlbumArtistsElseItsArtists() {
        val withBoth = mapOf(
            "ALBUMARTIST" to listOf("Strahan Coleman"),
            "ARTIST" to listOf("Narrator")
        )
        val artistsOnly = mapOf("ARTIST" to listOf("Neil Gaiman; Terry Pratchett"))

        assertEquals(listOf("Strahan Coleman"), TagReading.book(withBoth, "b.m4b").authors)
        assertEquals(
            "Neil Gaiman, Terry Pratchett",
            TagReading.book(artistsOnly, "b.m4b").author
        )
    }

    @Test
    fun readsABooksGenresAndYear() {
        val fields = mapOf(
            "GENRE" to listOf("Christianity; Spirituality"),
            "DATE" to listOf("2023")
        )

        val book = TagReading.book(fields, "b.m4b")

        assertEquals(listOf("Christianity", "Spirituality"), book.genres)
        assertEquals(2023, book.year)
    }

    @Test
    fun theFileTitleDropsOnlyTheLastExtension() {
        assertEquals("01 One More Time", TagReading.fileTitle("01 One More Time.flac"))
        assertEquals("Vol. 2", TagReading.fileTitle("Vol. 2.mp3"))
        assertEquals("README", TagReading.fileTitle("README"))
        assertEquals(".flac", TagReading.fileTitle(".flac"))
    }
}
