package com.jpd.hz.home

import com.jpd.hz.home.HomeCategory.ALBUMS
import com.jpd.hz.home.HomeCategory.ALBUM_ARTISTS
import com.jpd.hz.home.HomeCategory.AUDIO_BOOKS
import com.jpd.hz.home.HomeCategory.GENRES
import com.jpd.hz.home.HomeCategory.PLAYLISTS
import com.jpd.hz.home.HomeCategory.SONGS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    private val all = listOf(ALBUMS, ALBUM_ARTISTS, GENRES, SONGS, PLAYLISTS, AUDIO_BOOKS)

    @Test
    fun `nothing saved gives all six in today's order, all shown`() {
        val layout = homeLayoutOf(null, null)

        assertEquals(HomeLayout.DEFAULT, layout)
        assertEquals(all, layout.visible)
        assertEquals("albums,album_artists,genres,songs,playlists,audio_books", layout.orderValue())
    }

    @Test
    fun `a saved order and hidden set read back as saved`() {
        val saved = HomeLayout(all.reversed(), setOf(GENRES))

        val read = homeLayoutOf(saved.orderValue(), saved.hidden.map { it.key }.toSet())

        assertEquals(saved, read)
        assertEquals(all.reversed() - GENRES, read.visible)
    }

    @Test
    fun `unknown and repeated keys are dropped`() {
        val layout = homeLayoutOf(
            "songs,radio,songs,albums,album_artists,genres,playlists,audio_books",
            setOf("radio")
        )

        assertEquals(
            listOf(SONGS, ALBUMS, ALBUM_ARTISTS, GENRES, PLAYLISTS, AUDIO_BOOKS),
            layout.order
        )
        assertTrue(layout.hidden.isEmpty())
    }

    @Test
    fun `a category missing from the order joins the end, shown`() {
        val layout = homeLayoutOf("songs,albums,genres", setOf("albums", "playlists"))

        assertEquals(
            listOf(SONGS, ALBUMS, GENRES, ALBUM_ARTISTS, PLAYLISTS, AUDIO_BOOKS),
            layout.order
        )
        assertEquals(setOf(ALBUMS), layout.hidden)
    }

    @Test
    fun `a hidden set covering every category is cleared`() {
        val layout = homeLayoutOf(HomeLayout.DEFAULT.orderValue(), all.map { it.key }.toSet())

        assertTrue(layout.hidden.isEmpty())
        assertEquals(all, layout.visible)
    }

    @Test
    fun `the last shown category can't be hidden`() {
        val oneLeft = HomeLayout(all, (all - SONGS).toSet())

        assertFalse(oneLeft.canHide(SONGS))
        // Already hidden, so hiding it again is allowed and changes nothing.
        assertTrue(oneLeft.canHide(ALBUMS))
        assertSame(oneLeft, oneLeft.withHidden(SONGS, hidden = true))

        val twoLeft = oneLeft.withHidden(ALBUMS, hidden = false)
        assertEquals(listOf(ALBUMS, SONGS), twoLeft.visible)
        assertTrue(twoLeft.canHide(SONGS))
        assertEquals(listOf(ALBUMS), twoLeft.withHidden(SONGS, hidden = true).visible)
    }

    @Test
    fun `moved takes a category down or up the order`() {
        val down = HomeLayout.DEFAULT.moved(fromIndex = 0, toIndex = 2)
        val up = HomeLayout.DEFAULT.moved(fromIndex = 5, toIndex = 1)

        assertEquals(
            listOf(ALBUM_ARTISTS, GENRES, ALBUMS, SONGS, PLAYLISTS, AUDIO_BOOKS),
            down.order
        )
        assertEquals(
            listOf(ALBUMS, AUDIO_BOOKS, ALBUM_ARTISTS, GENRES, SONGS, PLAYLISTS),
            up.order
        )
    }
}
