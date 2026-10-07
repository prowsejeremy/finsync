package com.jpd.hz.adapter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The tag-then-rename steps (spec "Each track or book during a sync", "Later syncs"). */
class AdapterFilesTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val fields = mapOf("TITLE" to "One More Time")
    private val tagged = "audio{TITLE=One More Time}"

    private fun download(text: String = "audio"): Pair<File, File> {
        val target = File(temp.root, "01 One More Time.flac")
        val part = AdapterFiles.partOf(target).apply { writeText(text) }
        return part to target
    }

    @Test
    fun `a part file is the file's path plus dot part`() {
        assertEquals("/sync/a.m4b.part", AdapterFiles.partOf(File("/sync/a.m4b")).path)
    }

    @Test
    fun `a tagged download is renamed into place, tagged with its real extension`() {
        val (part, target) = download()
        val tagger = FakeTagger()

        assertEquals(TagResult.TAGGED, AdapterFiles.finishDownload(part, target, fields, tagger))
        assertEquals(tagged, target.readText())
        assertFalse(part.exists())
        assertEquals(listOf(part.path to "flac"), tagger.writes)
    }

    @Test
    fun `a download TagLib can't open is kept as downloaded`() {
        val (part, target) = download()
        val tagger = FakeTagger(opens = false)

        assertEquals(TagResult.UNREADABLE, AdapterFiles.finishDownload(part, target, fields, tagger))
        assertEquals("audio", target.readText())
        assertFalse(part.exists())
        assertTrue(tagger.writes.isEmpty())
    }

    @Test
    fun `a failed write deletes the download, which may be damaged, and leaves no file`() {
        val (part, target) = download()

        val result = AdapterFiles.finishDownload(part, target, fields, FakeTagger(succeeds = false))

        assertEquals(TagResult.FAILED, result)
        assertFalse(part.exists())
        assertFalse(target.exists())
    }

    @Test
    fun `a download replaces the old file only once it's tagged`() {
        val (part, target) = download()
        target.writeText("old")

        AdapterFiles.finishDownload(part, target, fields, FakeTagger())

        assertEquals(tagged, target.readText())
    }

    @Test
    fun `with no fields to write, nothing is written and the download still lands`() {
        val (part, target) = download()
        val tagger = FakeTagger()

        assertEquals(TagResult.TAGGED, AdapterFiles.finishDownload(part, target, emptyMap(), tagger))
        assertEquals("audio", target.readText())
        assertTrue(tagger.writes.isEmpty())
    }

    @Test
    fun `a re-tag writes a copy, then renames it over the original`() {
        val file = File(temp.root, "book.m4b").apply { writeText("audio") }
        val tagger = FakeTagger()

        assertEquals(TagResult.TAGGED, AdapterFiles.retag(file, fields, tagger))
        assertEquals(tagged, file.readText())
        assertFalse(AdapterFiles.partOf(file).exists())
        assertEquals(listOf(AdapterFiles.partOf(file).path to "m4b"), tagger.writes)
    }

    @Test
    fun `a failed re-tag leaves the original exactly as it was, and no part file`() {
        val bytes = byteArrayOf(0, 1, 2, 3, 4)
        val file = File(temp.root, "track.mp3").apply { writeBytes(bytes) }

        assertEquals(TagResult.FAILED, AdapterFiles.retag(file, fields, FakeTagger(succeeds = false)))
        assertArrayEquals(bytes, file.readBytes())
        assertFalse(AdapterFiles.partOf(file).exists())
    }

    @Test
    fun `a file TagLib can't open isn't copied or written`() {
        val file = File(temp.root, "track.mp3").apply { writeText("audio") }
        val tagger = FakeTagger(opens = false)

        assertEquals(TagResult.UNREADABLE, AdapterFiles.retag(file, fields, tagger))
        assertEquals("audio", file.readText())
        assertFalse(AdapterFiles.partOf(file).exists())
        assertTrue(tagger.writes.isEmpty())
    }

    @Test
    fun `a re-tag with no fields is already done`() {
        val file = File(temp.root, "track.mp3").apply { writeText("audio") }
        val tagger = FakeTagger()

        assertEquals(TagResult.TAGGED, AdapterFiles.retag(file, emptyMap(), tagger))
        assertEquals("audio", file.readText())
        assertTrue(tagger.writes.isEmpty())
    }

    @Test
    fun `a copy is made when the target is missing or a different size, not when it matches`() {
        val source = File(temp.root, "photo.jpg").apply { writeText("photo") }
        val target = File(temp.root, "Music/Daft Punk/artist.jpg")
        target.parentFile!!.mkdirs()

        AdapterFiles.copyIfChanged(source, target)
        assertEquals("photo", target.readText())

        target.writeText("PHOTO")
        AdapterFiles.copyIfChanged(source, target)
        assertEquals("PHOTO", target.readText())

        target.writeText("old photo")
        AdapterFiles.copyIfChanged(source, target)
        assertEquals("photo", target.readText())
        assertFalse(AdapterFiles.partOf(target).exists())
    }

    @Test
    fun `text is written as UTF-8 with no byte-order mark, and an unchanged file isn't rewritten`() {
        val target = File(temp.root, "Playlists/Café.m3u8")

        AdapterFiles.writeIfChanged(target, "#EXTM3U\nCafé\n")
        assertArrayEquals("#EXTM3U\nCafé\n".toByteArray(Charsets.UTF_8), target.readBytes())

        val earlier = 1_000_000L
        target.setLastModified(earlier)
        AdapterFiles.writeIfChanged(target, "#EXTM3U\nCafé\n")
        assertEquals(earlier, target.lastModified())

        AdapterFiles.writeIfChanged(target, "#EXTM3U\n")
        assertEquals("#EXTM3U\n", target.readText())
        assertFalse(AdapterFiles.partOf(target).exists())
    }
}
