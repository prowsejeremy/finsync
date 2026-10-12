package com.jpd.hz.library.scan

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EmbeddedArtTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val tags = FakeTagSource()
    private val cache by lazy { File(temp.root, "embedded_art") }
    private val art by lazy { EmbeddedArt(cache, tags) }
    private val source = File("/library/X/01.mp3")

    @Test
    fun aCoverIsCachedAndReadAgainOnlyWhenItsSourceChanged() {
        tags.coversByName["01.mp3"] = byteArrayOf(1, 2, 3)

        val first = art.coverFor("album", "X/01.mp3", source, changed = false)!!
        val again = art.coverFor("album", "X/01.mp3", source, changed = false)!!
        tags.coversByName["01.mp3"] = byteArrayOf(4)
        val changed = art.coverFor("album", "X/01.mp3", source, changed = true)!!

        assertEquals(first, again)
        assertEquals(first, changed)
        assertArrayEquals(byteArrayOf(4), changed.readBytes())
        assertEquals(2, tags.coverReads.size)
        assertEquals(cache, first.parentFile)
    }

    @Test
    fun aSourceWithNoCoverIsRememberedUntilItChanges() {
        assertNull(art.coverFor("album", "X/01.mp3", source, changed = false))
        assertNull(art.coverFor("album", "X/01.mp3", source, changed = false))

        assertEquals(1, tags.coverReads.size)
    }

    @Test
    fun anotherSourceGetsItsOwnFile() {
        tags.coversByName["01.mp3"] = byteArrayOf(1)
        tags.coversByName["02.mp3"] = byteArrayOf(2)

        val first = art.coverFor("album", "X/01.mp3", source, changed = false)!!
        val second = art.coverFor("album", "X/02.mp3", File("/library/X/02.mp3"), changed = false)!!

        assertEquals(2, cache.list()!!.size)
        assertArrayEquals(byteArrayOf(2), second.readBytes())
        assertNotEquals(first, second)
    }

    @Test
    fun filesTheLastPassDidntAskForAreDeleted() {
        tags.coversByName["01.mp3"] = byteArrayOf(1)
        art.coverFor("gone", "X/01.mp3", source, changed = false)
        art.coverFor("kept", "X/01.mp3", source, changed = false)
        art.coverFor("none", "Y/03.mp3", File("/library/Y/03.mp3"), changed = false)
        File(cache, "stray.part").writeBytes(byteArrayOf(0))

        art.startPass()
        val kept = art.coverFor("kept", "X/01.mp3", source, changed = false)!!
        assertNull(art.coverFor("none", "Y/03.mp3", File("/library/Y/03.mp3"), changed = false))
        art.deleteUnused()

        assertEquals(2, cache.list()!!.size)
        assertEquals(true, kept.isFile)
        assertEquals(3, tags.coverReads.size)
    }
}
