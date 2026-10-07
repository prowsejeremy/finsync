package com.jpd.hz.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalisingTest {

    @Test
    fun trimsCollapsesWhitespaceAndLowerCases() {
        assertEquals("daft punk", Normalising.normalise("  Daft \t Punk "))
    }

    @Test
    fun collapsesNoBreakSpacesToo() {
        assertEquals("daft punk", Normalising.normalise("Daft  Punk"))
    }

    @Test
    fun lowerCasesWithoutTheDevicesLocale() {
        assertEquals("title", Normalising.normalise("TITLE"))
    }

    @Test
    fun anAlbumIdJoinsAlbumArtistsAndTheAlbumName() {
        assertEquals(
            "daft punk; pharrell williams\u001Frandom access memories",
            Normalising.albumIdOf(
                listOf("Daft Punk", "Pharrell Williams"),
                "Random Access  Memories"
            )
        )
    }

    @Test
    fun discFoldersOfOneAlbumShareAnId() {
        val cd1 = Normalising.albumIdOf(listOf("Pink Floyd"), "The Wall")
        val cd2 = Normalising.albumIdOf(listOf("pink floyd "), " The Wall")

        assertEquals(cd1, cd2)
    }

    @Test
    fun theSameAlbumNameByDifferentAlbumArtistsIsTwoAlbums() {
        assertNotEquals(
            Normalising.albumIdOf(listOf("Weezer"), "Weezer"),
            Normalising.albumIdOf(listOf("Various Artists"), "Weezer")
        )
    }

    @Test
    fun aTrackWithNoAlbumHasNoAlbumId() {
        assertNull(Normalising.albumIdOf(listOf("Daft Punk"), null))
        assertNull(Normalising.albumIdOf(listOf("Daft Punk"), "  "))
    }

    @Test
    fun anAlbumWithNoAlbumArtistStillHasAnId() {
        assertEquals("\u001Fdiscovery", Normalising.albumIdOf(emptyList(), "Discovery"))
    }
}
