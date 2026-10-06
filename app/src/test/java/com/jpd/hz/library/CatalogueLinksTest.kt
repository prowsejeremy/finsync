package com.jpd.hz.library

import com.jpd.hz.db.CatalogueAlbumArtist
import com.jpd.hz.db.CatalogueArtist
import com.jpd.hz.db.CatalogueGenre
import com.jpd.hz.db.CatalogueTrackArtist
import com.jpd.hz.db.CatalogueTrackGenre
import com.jpd.hz.model.MediaItem
import com.jpd.hz.model.NameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueLinksTest {

    private val kurt = NameId(name = "Kurt Vile", id = "kurt")
    private val kim = NameId(name = "Kim Gordon", id = "kim")
    private val rock = NameId(name = "Indie Rock", id = "rock")

    private fun audio(
        id: String,
        albumId: String? = "alb1",
        albumArtists: List<NameId>? = null,
        artistItems: List<NameId>? = null,
        genreItems: List<NameId>? = null
    ) = MediaItem(
        id = id,
        name = "Track $id",
        type = "Audio",
        albumId = albumId,
        artistItems = artistItems,
        albumArtists = albumArtists,
        genreItems = genreItems
    )

    @Test
    fun `an album's album artists are its tracks' credits in first-seen order`() {
        val catalogue = catalogueFrom(
            listOf(
                audio("t1", albumArtists = listOf(kurt)),
                audio("t2", albumArtists = listOf(kim, kurt))
            )
        )
        assertEquals(
            listOf(
                CatalogueAlbumArtist("alb1", "kurt", 0),
                CatalogueAlbumArtist("alb1", "kim", 1)
            ),
            catalogue.albumArtists
        )
    }

    @Test
    fun `track artists keep server order without duplicate pairs`() {
        val catalogue = catalogueFrom(listOf(audio("t1", artistItems = listOf(kim, kurt, kim))))
        assertEquals(
            listOf(CatalogueTrackArtist("t1", "kim", 0), CatalogueTrackArtist("t1", "kurt", 1)),
            catalogue.trackArtists
        )
    }

    @Test
    fun `track genres have no duplicate pairs`() {
        val catalogue = catalogueFrom(listOf(audio("t1", genreItems = listOf(rock, rock))))
        assertEquals(listOf(CatalogueTrackGenre("t1", "rock")), catalogue.trackGenres)
    }

    @Test
    fun `artists are the union of album and track artists, and the first name wins`() {
        val renamed = NameId(name = "K. Vile", id = "kurt")
        val catalogue = catalogueFrom(
            listOf(
                audio("t1", albumArtists = listOf(kurt)),
                audio("t2", artistItems = listOf(renamed, kim))
            )
        )
        assertEquals(
            listOf(CatalogueArtist("kurt", "Kurt Vile"), CatalogueArtist("kim", "Kim Gordon")),
            catalogue.artists
        )
    }

    @Test
    fun `genres are the union across tracks, and the first name wins`() {
        val renamed = NameId(name = "Indie", id = "rock")
        val jazz = NameId(name = "Jazz", id = "jazz")
        val catalogue = catalogueFrom(
            listOf(
                audio("t1", genreItems = listOf(rock)),
                audio("t2", genreItems = listOf(renamed, jazz))
            )
        )
        assertEquals(
            listOf(CatalogueGenre("rock", "Indie Rock"), CatalogueGenre("jazz", "Jazz")),
            catalogue.genres
        )
    }

    @Test
    fun `a track without an album keeps its artists and genres but adds no album artists`() {
        val catalogue = catalogueFrom(
            listOf(
                audio(
                    "t1",
                    albumId = null,
                    albumArtists = listOf(kurt),
                    artistItems = listOf(kurt),
                    genreItems = listOf(rock)
                )
            )
        )
        assertTrue(catalogue.albumArtists.isEmpty())
        assertEquals(listOf(CatalogueTrackArtist("t1", "kurt", 0)), catalogue.trackArtists)
        assertEquals(listOf(CatalogueTrackGenre("t1", "rock")), catalogue.trackGenres)
    }

    @Test
    fun `credits with a blank id or name are dropped`() {
        val catalogue = catalogueFrom(
            listOf(
                audio(
                    "t1",
                    artistItems = listOf(
                        NameId(name = "Nobody", id = ""),
                        NameId(name = "", id = "x"),
                        kurt
                    )
                )
            )
        )
        assertEquals(listOf(CatalogueTrackArtist("t1", "kurt", 0)), catalogue.trackArtists)
        assertEquals(listOf(CatalogueArtist("kurt", "Kurt Vile")), catalogue.artists)
    }
}
