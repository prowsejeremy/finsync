package com.jpd.hz.adapter.files

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared layout every server platform builds its paths with, so every server lays files out
 * alike (adapter harness spec, "The contract").
 */
class LibraryLayoutTest {

    @Test
    fun `a song goes under its album artist and album`() {
        assertEquals(
            "Music/Daft Punk/Discovery/01 One More Time.flac",
            LibraryLayout.musicPath("Daft Punk", "Discovery", "01 One More Time.flac")
        )
    }

    @Test
    fun `each part of a song's path is made safe on its own`() {
        assertEquals(
            "Music/AC_DC/Back_In Black/What_.flac",
            LibraryLayout.musicPath(" AC/DC ", "Back:In Black", "What?.flac")
        )
    }

    @Test
    fun `a book goes under its author and title, each part made safe`() {
        assertEquals(
            "Audiobooks/Andy Weir/Project Hail Mary/phm.m4b",
            LibraryLayout.bookPath("Andy Weir", "Project Hail Mary", "phm.m4b")
        )
        assertEquals(
            "Audiobooks/Weir_ Andy/Why_ A Book/part _1_.m4b",
            LibraryLayout.bookPath("Weir| Andy", "Why* A Book", "part <1>.m4b")
        )
    }

    @Test
    fun `an artist's photo goes in its album artist's folder`() {
        assertEquals("Music/Daft Punk/artist.jpg", LibraryLayout.artistPhotoPath("Daft Punk"))
        assertEquals("Music/AC_DC/artist.jpg", LibraryLayout.artistPhotoPath("AC/DC"))
    }

    @Test
    fun `a cover goes beside the file, in a folder or at the root`() {
        assertEquals(
            "Music/Daft Punk/Discovery/folder.jpg",
            LibraryLayout.coverBeside("Music/Daft Punk/Discovery/01 One More Time.flac")
        )
        assertEquals("folder.jpg", LibraryLayout.coverBeside("song.flac"))
    }

    @Test
    fun `a playlist's file and cover sit side by side in Playlists`() {
        assertEquals("Playlists/Road Trip.m3u8", LibraryLayout.playlistFilePath("Road Trip"))
        assertEquals("Playlists/Road Trip.jpg", LibraryLayout.playlistCoverPath("Road Trip"))
    }

    @Test
    fun `a playlist line points from Playlists up to the file`() {
        assertEquals(
            "../Music/Daft Punk/Discovery/01 One More Time.flac",
            LibraryLayout.playlistEntryPath("Music/Daft Punk/Discovery/01 One More Time.flac")
        )
    }
}
