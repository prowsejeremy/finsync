package com.jpd.finsync.ui

import com.jpd.finsync.library.AlbumSummary
import com.jpd.finsync.library.GroupDetail
import com.jpd.finsync.library.SongRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupFormatTest {

    private fun album(id: String, artwork: String? = "/art/$id.jpg") = AlbumSummary(
        albumId = id,
        name = "Album $id",
        albumArtist = null,
        year = null,
        downloadedTrackCount = 1,
        artworkPath = artwork
    )

    private fun song(id: String, albumId: String?) = SongRow(
        itemId = id,
        title = "Song $id",
        artists = null,
        albumId = albumId,
        albumName = null,
        albumYear = null,
        discNumber = null,
        trackNumber = null,
        durationMs = null,
        artworkPath = null
    )

    private fun group(albums: List<AlbumSummary>, songs: List<SongRow>, showsAllSongs: Boolean) =
        GroupDetail(
            id = "g",
            name = "Group",
            photoPath = null,
            albums = albums,
            songs = songs,
            showsAllSongs = showsAllSongs
        )

    @Test
    fun `two words give two initials`() {
        assertEquals("LH", initialsOf("Lucy Harrow"))
        assertEquals("VA", initialsOf("Various Artists"))
    }

    @Test
    fun `one word gives one initial`() {
        assertEquals("R", initialsOf("Radiohead"))
    }

    @Test
    fun `only the first two words count, in capitals`() {
        assertEquals("TB", initialsOf("the black keys"))
    }

    @Test
    fun `words without letters are skipped`() {
        assertEquals("SG", initialsOf("Simon & Garfunkel"))
    }

    @Test
    fun `a name without letters has no initials`() {
        assertNull(initialsOf("2814"))
        assertNull(initialsOf("  "))
        assertNull(initialsOf(""))
    }

    @Test
    fun `a list under an hour reads in minutes`() {
        assertEquals(ListLength.Minutes(41), listLengthOf(listOf(2_400_000L, 60_000L, null)))
    }

    @Test
    fun `a list over an hour reads in hours and minutes`() {
        // 10,919,000 ms rounds to 182 minutes.
        assertEquals(ListLength.HoursMinutes(3, 2), listLengthOf(listOf(10_890_000L, 29_000L)))
    }

    @Test
    fun `whole hours leave the minutes out`() {
        assertEquals(ListLength.Hours(3), listLengthOf(listOf(5_400_000L, 5_400_000L)))
    }

    @Test
    fun `the group list has all songs, an albums label and the albums`() {
        val albums = listOf(album("a"), album("b"), album("c"), album("d", artwork = null))
        val songs = listOf(song("s1", "a"))
        assertEquals(
            listOf(
                GroupRow.AllSongs(listOf("/art/a.jpg", "/art/b.jpg", "/art/c.jpg"), songs),
                GroupRow.AlbumsLabel,
                GroupRow.Album(albums[0]),
                GroupRow.Album(albums[1]),
                GroupRow.Album(albums[2]),
                GroupRow.Album(albums[3])
            ),
            groupRows(group(albums, songs, showsAllSongs = true))
        )
    }

    @Test
    fun `all songs and the albums label are left out when they have nothing to show`() {
        val oneAlbum = listOf(album("a"))
        assertEquals(
            listOf(GroupRow.AlbumsLabel, GroupRow.Album(oneAlbum[0])),
            groupRows(group(oneAlbum, listOf(song("s1", "a")), showsAllSongs = false))
        )
        val loose = listOf(song("s2", null))
        assertEquals(
            listOf(GroupRow.AllSongs(emptyList(), loose)),
            groupRows(group(emptyList(), loose, showsAllSongs = true))
        )
    }
}
