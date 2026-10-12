package com.jpd.hz.library.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanRulesTest {

    private fun images(vararg paths: String) = paths.associateBy { it.lowercase() }

    @Test
    fun audioIsTheTypesBassPlaysInAnyCase() {
        listOf("a.MP3", "b.flac", "c.m4a", "d.M4B", "e.ogg", "f.opus", "g.ape", "h.wv", "i.wav",
            "j.aif", "k.AIFF").forEach { assertTrue(it, ScanRules.isAudio(it)) }
        listOf("a.mp3.part", "b.txt", "c.m3u8", "noextension", "d.jpg")
            .forEach { assertFalse(it, ScanRules.isAudio(it)) }
    }

    @Test
    fun playlistsAndImages() {
        assertTrue(ScanRules.isPlaylist("Road Trip.M3U8"))
        assertTrue(ScanRules.isPlaylist("old.m3u"))
        assertTrue(ScanRules.isImage("Folder.JPG"))
        assertTrue(ScanRules.isImage("artist.png"))
        assertFalse(ScanRules.isImage("cover.jpeg"))
    }

    @Test
    fun hiddenAndNoMedia() {
        assertTrue(ScanRules.isHidden(".thumbnails"))
        assertFalse(ScanRules.isHidden("Music"))
        assertTrue(ScanRules.isNoMedia(".NoMedia"))
        assertFalse(ScanRules.isNoMedia("nomedia"))
    }

    @Test
    fun aBookHasAnAudiobooksFolderAnywhereInItsPath() {
        assertTrue(ScanRules.isBook("kurage/Audiobooks/Author/Title/book.m4b"))
        assertTrue(ScanRules.isBook("AUDIOBOOKS/book.mp3"))
        assertTrue(ScanRules.isBook("Mine/audiobooks/Kids/story.mp3"))
        assertFalse(ScanRules.isBook("Music/Audiobooks.mp3"))
        assertFalse(ScanRules.isBook("My Audiobooks/book.m4b"))
        assertFalse(ScanRules.isBook("Music/Artist/Album/01.mp3"))
    }

    @Test
    fun extensionAndNames() {
        assertEquals("mp3", ScanRules.extensionOf("A/b.c/01 Song.MP3"))
        assertEquals("", ScanRules.extensionOf("folder.d/README"))
        assertEquals("01.mp3", ScanRules.fileNameOf("A/B/01.mp3"))
        assertEquals("A/B", ScanRules.folderOf("A/B/01.mp3"))
        assertEquals("", ScanRules.folderOf("01.mp3"))
        assertEquals("x.mp3", ScanRules.childOf("", "x.mp3"))
        assertEquals("A/x.mp3", ScanRules.childOf("A", "x.mp3"))
    }

    @Test
    fun folderArtFollowsTheOrderAndIgnoresCase() {
        val found = images("Music/A/Album/cover.jpg", "Music/A/Album/Folder.PNG")
        assertEquals("Music/A/Album/Folder.PNG", ScanRules.folderArt("Music/A/Album/01.mp3", found))
        assertEquals(
            "Music/A/Album/cover.jpg",
            ScanRules.folderArt("Music/A/Album/01.mp3", images("Music/A/Album/cover.jpg"))
        )
        assertNull(ScanRules.folderArt("Music/B/01.mp3", found))
        assertEquals("folder.jpg", ScanRules.folderArt("01.mp3", images("folder.jpg")))
    }

    @Test
    fun artistPhotoIsInTheFolderAboveTheAlbumButNeverTheLibraryFolder() {
        val found = images("Music/Daft Punk/Artist.JPG", "Music/Daft Punk/artist.png", "artist.jpg")
        assertEquals(
            "Music/Daft Punk/Artist.JPG",
            ScanRules.artistPhoto("Music/Daft Punk/Discovery/01.flac", found)
        )
        assertNull(ScanRules.artistPhoto("Discovery/01.flac", found))
        assertNull(ScanRules.artistPhoto("01.flac", found))
        assertEquals(
            "Music/X/artist.png",
            ScanRules.artistPhoto("Music/X/Album/01.mp3", images("Music/X/artist.png"))
        )
    }

    @Test
    fun playlistCoverHasThePlaylistsName() {
        val found = images("kurage/Playlists/road trip.PNG")
        assertEquals(
            "kurage/Playlists/road trip.PNG",
            ScanRules.playlistCover("kurage/Playlists/Road Trip.m3u8", found)
        )
        assertNull(ScanRules.playlistCover("kurage/Playlists/Other.m3u8", found))
    }
}
