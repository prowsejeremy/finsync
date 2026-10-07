package com.jpd.hz.tags

import java.io.File
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Every test runs once per sample format, on a ".part" copy opened with its real extension. */
@RunWith(Parameterized::class)
class TagLibBridgeTest(private val sample: Sample) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun samples(): List<Sample> = SAMPLES

        private const val DURATION_TOLERANCE_MS = 10L
        private const val BITRATE_TOLERANCE = 1_000L

        private val MUSIC_VALUES = mapOf(
            "TITLE" to "Café — 東京 🎧",
            "ARTIST" to "Daft Punk; Pharrell Williams",
            "ALBUM" to "Random Access Memories",
            "ALBUMARTIST" to "Daft Punk",
            "GENRE" to "Electronic; Disco",
            "DATE" to "2013",
            "TRACKNUMBER" to "8",
            "DISCNUMBER" to "1"
        )
        private val BOOK_VALUES = MUSIC_VALUES - listOf("TRACKNUMBER", "DISCNUMBER")
    }

    private val files = SampleFiles()
    private val ourValues = if (sample.isBook) BOOK_VALUES else MUSIC_VALUES

    @Before
    fun setUp() = files.reset()

    @After
    fun tearDown() {
        files.directory.deleteRecursively()
    }

    @Test
    fun readsTheAudioDetails() {
        val audio = read(files.copy(sample.name)).audio

        assertEquals(sample.codec, audio.codec)
        assertEquals(sample.sampleRate, audio.sampleRate)
        assertEquals(sample.bitDepth, audio.bitDepth)
        assertNear("durationMs", sample.durationMs, audio.durationMs, DURATION_TOLERANCE_MS)
        assertNear("bitrate", sample.bitrate.toLong(), audio.bitrate?.toLong(), BITRATE_TOLERANCE)
    }

    @Test
    fun writesOurFieldsAndKeepsEveryOtherField() {
        val file = files.copy(sample.name)
        val before = read(file)

        assertTrue(TagLibBridge.write(file.path, sample.extension, ourValues))

        val after = read(file)
        ourValues.forEach { (field, value) ->
            assertEquals(field, listOf(value), after.fields[field])
        }
        assertEquals(before.fields - ourValues.keys, after.fields - ourValues.keys)
        assertEquals(before.audio, after.audio)
    }

    @Test
    fun aSecondWriteChangesOnlyTheFieldItNames() {
        val file = files.copy(sample.name)
        assertTrue(TagLibBridge.write(file.path, sample.extension, ourValues))
        val firstWrite = read(file).fields

        assertTrue(TagLibBridge.write(file.path, sample.extension, mapOf("GENRE" to "Ambient")))

        assertEquals(firstWrite + ("GENRE" to listOf("Ambient")), read(file).fields)
    }

    @Test
    fun readsTheCoverBeforeAndAfterWriting() {
        val file = files.copy(sample.name)
        val expected = sample.cover?.let(files::bytes)

        assertSameBytes(expected, TagLibBridge.readCover(file.path, sample.extension))
        assertTrue(TagLibBridge.write(file.path, sample.extension, ourValues))
        assertSameBytes(expected, TagLibBridge.readCover(file.path, sample.extension))
    }

    @Test
    fun readsChaptersBeforeAndAfterWriting() {
        val file = files.copy(sample.name)
        val (nero, quickTime) = when (sample.chapters) {
            ChapterFormats.NONE -> emptyList<Chapter>() to emptyList()
            ChapterFormats.BOTH -> SAMPLE_CHAPTERS to SAMPLE_CHAPTERS
            ChapterFormats.NERO -> SAMPLE_CHAPTERS to emptyList()
            ChapterFormats.QUICK_TIME -> emptyList<Chapter>() to SAMPLE_CHAPTERS
        }

        assertChapters(read(file), nero, quickTime)
        assertTrue(TagLibBridge.write(file.path, sample.extension, ourValues))
        assertChapters(read(file), nero, quickTime)
    }

    private fun read(file: File): FileTags {
        val tags = TagLibBridge.read(file.path, sample.extension)
        assertNotNull("TagLib couldn't open ${file.name}", tags)
        return tags!!
    }

    private fun assertChapters(tags: FileTags, nero: List<Chapter>, quickTime: List<Chapter>) {
        assertEquals("Nero chapters", nero, tags.neroChapters)
        assertEquals("QuickTime chapters", quickTime, tags.quickTimeChapters)
    }

    private fun assertSameBytes(expected: ByteArray?, actual: ByteArray?) {
        if (expected == null) {
            assertNull("cover", actual)
        } else {
            assertArrayEquals("cover", expected, actual)
        }
    }

    private fun assertNear(what: String, expected: Long, actual: Long?, tolerance: Long) {
        assertNotNull(what, actual)
        assertTrue(
            "$what: expected $expected ± $tolerance, was $actual",
            abs(expected - actual!!) <= tolerance
        )
    }
}
