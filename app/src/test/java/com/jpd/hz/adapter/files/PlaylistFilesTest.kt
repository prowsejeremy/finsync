package com.jpd.hz.adapter.files

import org.junit.Assert.assertEquals
import org.junit.Test

/** Playlist files as adapters write them (spec "Playlist files"). */
class PlaylistFilesTest {

    @Test
    fun `a playlist file is the spec's example, line for line`() {
        val text = PlaylistFiles.contentOf(
            "Road Trip",
            listOf(
                PlaylistEntry(
                    "../Music/Daft Punk/Discovery/01 One More Time.flac",
                    215,
                    "Daft Punk - One More Time"
                )
            )
        )

        assertEquals(
            "#EXTM3U\n" +
                "#PLAYLIST:Road Trip\n" +
                "#EXTINF:215,Daft Punk - One More Time\n" +
                "../Music/Daft Punk/Discovery/01 One More Time.flac\n",
            text
        )
    }

    @Test
    fun `an unknown length is -1, and line breaks in names can't split a line`() {
        val text = PlaylistFiles.contentOf(
            "Road\nTrip",
            listOf(PlaylistEntry("../Music/a.mp3", null, "A\r\nB"))
        )

        assertEquals("#EXTM3U\n#PLAYLIST:Road Trip\n#EXTINF:-1,A B\n../Music/a.mp3\n", text)
    }

    @Test
    fun `an empty playlist is just its header and name`() {
        assertEquals("#EXTM3U\n#PLAYLIST:Empty\n", PlaylistFiles.contentOf("Empty", emptyList()))
    }

    @Test
    fun `names are made safe, and colliding names are numbered by name, then ID, ignoring case`() {
        val names = PlaylistFiles.fileNamesOf(
            listOf(
                "p3" to "road trip",
                "p1" to "Road Trip",
                "p2" to "Road Trip",
                "p4" to "AC/DC: Live",
                "p5" to ".hidden",
                "p6" to " "
            )
        )

        assertEquals("AC_DC_ Live", names["p4"])
        assertEquals("hidden", names["p5"])
        assertEquals("Playlist", names["p6"])
        assertEquals("Road Trip", names["p1"])
        assertEquals("Road Trip (2)", names["p2"])
        assertEquals("road trip (3)", names["p3"])
    }

    @Test
    fun `a playlist named like a numbered one still gets its own file name`() {
        val names = PlaylistFiles.fileNamesOf(
            listOf("a" to "Mix", "b" to "Mix", "c" to "Mix (2)")
        )

        assertEquals(mapOf("a" to "Mix", "b" to "Mix (2)", "c" to "Mix (2) (2)"), names)
    }
}
