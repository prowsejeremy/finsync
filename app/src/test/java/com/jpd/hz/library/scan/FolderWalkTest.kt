package com.jpd.hz.library.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FolderWalkTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun file(path: String): File =
        File(temp.root, path).apply {
            parentFile?.mkdirs()
            writeText(path)
        }

    @Test
    fun collectsAudioPlaylistsAndImagesAndSkipsTheRest() {
        file("Music/A/Album/01.mp3")
        file("Music/A/Album/folder.jpg")
        file("Music/A/Album/02.mp3.part")
        file("Music/A/notes.txt")
        file("kurage/Playlists/Road Trip.m3u8")
        file(".hidden/x.mp3")
        file("Music/.y.mp3")
        file("Skipped/.nomedia")
        file("Skipped/Inner/z.mp3")

        val listing = walkLibrary(temp.root)!!

        assertEquals(listOf("Music/A/Album/01.mp3"), listing.audio.map { it.path })
        assertEquals(File(temp.root, "Music/A/Album/01.mp3"), listing.audio.single().file)
        assertEquals(listOf("kurage/Playlists/Road Trip.m3u8"), listing.playlists.map { it.path })
        assertEquals(
            mapOf("music/a/album/folder.jpg" to "Music/A/Album/folder.jpg"),
            listing.images
        )
    }

    @Test
    fun audioIsInPathOrder() {
        file("b/1.mp3")
        file("A/2.mp3")
        file("a.mp3")
        file("A/10.mp3")

        val paths = walkLibrary(temp.root)!!.audio.map { it.path }

        assertEquals(listOf("a.mp3", "A/10.mp3", "A/2.mp3", "b/1.mp3"), paths)
    }

    @Test
    fun aFolderThatCantBeListedGivesNull() {
        assertNull(walkLibrary(File(temp.root, "missing")))
    }

    @Test(expected = IllegalStateException::class)
    fun cancellingStopsTheWalk() {
        file("A/1.mp3")
        walkLibrary(temp.root) { throw IllegalStateException("cancelled") }
    }
}
