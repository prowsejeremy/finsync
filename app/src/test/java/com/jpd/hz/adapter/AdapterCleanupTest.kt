package com.jpd.hz.adapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Cleanup works only inside the adapter's folder (D4; device check 5). */
class AdapterCleanupTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(path: String): File =
        File(temp.root, path).apply {
            parentFile!!.mkdirs()
            writeText(path)
        }

    @Test
    fun `everything not kept goes, kept files stay whatever their case, and nothing outside is touched`() {
        val folder = File(temp.root, "Media/hz/kurage")
        val kept = file("Media/hz/kurage/Music/Daft Punk/Discovery/01 One More Time.flac")
        val photo = file("Media/hz/kurage/Music/Daft Punk/artist.jpg")
        val stray = file("Media/hz/kurage/Music/Daft Punk/Discovery/02 Aerodynamic.flac.part")
        val gone = file("Media/hz/kurage/Music/Old/Album/track.mp3")
        val playlist = file("Media/hz/kurage/Playlists/Road Trip.m3u8")
        val usersOwn = file("Media/hz/My Album/track.flac")
        val beside = file("Media/hz/kurage (Jellyfin)/Music/track.flac")

        cleanUpAdapterFolder(
            folder,
            setOf(
                kept.absolutePath,
                photo.absolutePath.uppercase(),
                playlist.absolutePath
            )
        )

        assertTrue(kept.isFile)
        assertTrue(photo.isFile)
        assertTrue(playlist.isFile)
        assertFalse(stray.exists())
        assertFalse(gone.exists())
        assertFalse(File(folder, "Music/Old").exists())
        assertTrue(usersOwn.isFile)
        assertTrue(beside.isFile)
        assertTrue(folder.isDirectory)
    }

    @Test
    fun `an empty keep set empties the folder but keeps it`() {
        val folder = File(temp.root, "kurage")
        file("kurage/Music/a/b/c.flac")

        cleanUpAdapterFolder(folder, emptySet())

        assertTrue(folder.isDirectory)
        assertEquals(0, folder.listFiles()!!.size)
    }
}
