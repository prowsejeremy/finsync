package com.jpd.hz.library.scan

import org.junit.Assert.assertEquals
import org.junit.Test

private const val LIBRARY = "/storage/emulated/0/Media/hz"
private const val PLAYLIST = "kurage/Playlists/Road.m3u8"

class PlaylistReadingTest {

    @Test
    fun readsWhatAdaptersWrite() {
        val text = "#EXTM3U\n#PLAYLIST:Road Trip\n#EXTINF:215,Daft Punk - One More Time\n" +
            "../Music/Daft Punk/Discovery/01 One More Time.flac\n" +
            "#EXTINF:-1,Unknown\n../Music/A/B/02.mp3\n"

        val parsed = PlaylistReading.parse(text, PLAYLIST, LIBRARY)

        assertEquals("Road Trip", parsed.name)
        assertEquals(
            listOf(
                "kurage/Music/Daft Punk/Discovery/01 One More Time.flac",
                "kurage/Music/A/B/02.mp3"
            ),
            parsed.entries
        )
    }

    @Test
    fun theNameFallsBackToTheFileName() {
        assertEquals("Road", PlaylistReading.parse("a.mp3\n", PLAYLIST, LIBRARY).name)
        assertEquals("Road", PlaylistReading.parse("#PLAYLIST:  \n", PLAYLIST, LIBRARY).name)
        assertEquals(
            "First",
            PlaylistReading.parse("#PLAYLIST:First\n#PLAYLIST:Second\n", PLAYLIST, LIBRARY).name
        )
    }

    @Test
    fun relativeEntriesAreResolvedFromThePlaylistsFolder() {
        val text = "\uFEFF#EXTM3U\r\nsong.mp3\r\n..\\Music\\A\\03.mp3\r\n" +
            "./A/./04.mp3\r\nA//05.mp3\r\n"

        val parsed = PlaylistReading.parse(text, PLAYLIST, LIBRARY)

        assertEquals(
            listOf(
                "kurage/Playlists/song.mp3",
                "kurage/Music/A/03.mp3",
                "kurage/Playlists/A/04.mp3",
                "kurage/Playlists/A/05.mp3"
            ),
            parsed.entries
        )
    }

    @Test
    fun absolutePathsInsideTheLibraryLoseTheFoldersPart() {
        val text = "/STORAGE/emulated/0/Media/hz/kurage/Music/A/06.mp3\n" +
            "/storage/emulated/0/Music/elsewhere.mp3\n" +
            "/storage/emulated/0/Media/hz\n"

        assertEquals(
            listOf("kurage/Music/A/06.mp3"),
            PlaylistReading.parse(text, PLAYLIST, LIBRARY).entries
        )
    }

    @Test
    fun entriesLeavingTheLibraryAreSkipped() {
        val text = "../../../outside.mp3\nhttp://example.com/x.mp3\nfile:///sdcard/y.mp3\n" +
            "C:\\Music\\z.mp3\n# a comment\n\n"

        assertEquals(emptyList<String>(), PlaylistReading.parse(text, PLAYLIST, LIBRARY).entries)
    }

    @Test
    fun aPlaylistAtTheLibrarysTopResolvesFromThere() {
        val parsed = PlaylistReading.parse("Music/x.mp3\n../y.mp3\n", "Mix.m3u", "$LIBRARY/")

        assertEquals("Mix", parsed.name)
        assertEquals(listOf("Music/x.mp3"), parsed.entries)
    }
}
