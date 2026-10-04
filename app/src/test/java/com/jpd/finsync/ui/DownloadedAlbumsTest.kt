package com.jpd.finsync.ui

import com.jpd.finsync.db.SyncedAlbum
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadedAlbumsTest {

    private val first = SyncedAlbum(albumId = "a1", name = "First", albumArtist = null, childCount = 3)
    private val second = SyncedAlbum(albumId = "a2", name = "Second", albumArtist = null, childCount = 5)
    private val albums = listOf(first, second)

    @Test
    fun `empty selection shows every album`() {
        assertEquals(albums, visibleDownloadedAlbums(albums, emptySet()))
    }

    @Test
    fun `selection containing all shows every album`() {
        assertEquals(albums, visibleDownloadedAlbums(albums, setOf("all", "a1")))
    }

    @Test
    fun `specific selection shows only those albums`() {
        assertEquals(listOf(second), visibleDownloadedAlbums(albums, setOf("a2", "missing")))
    }
}
