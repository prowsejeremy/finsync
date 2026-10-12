package com.jpd.hz.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Files TagLib must refuse or survive, and the bridge's own rules. */
@RunWith(AndroidJUnit4::class)
class TagLibBridgeSafetyTest {

    companion object {
        private const val TRUNCATED_BYTES = 3_000
        private const val NOT_AUDIO_REPEATS = 1_000
        private const val PADDING_BYTES = 4_096
        private const val ID3V2_HEADER_BYTES = 10
        private const val ID3V2_VERSION_AT = 3
        private const val ID3V2_SIZE_AT = 6
        private const val SYNCSAFE_BYTES = 4
        private const val SYNCSAFE_BITS = 7
        private const val SYNCSAFE_MASK = 0x7F
        private const val ID3V1_BYTES = 128
        private const val ID3V2_3 = 3
        private const val ID3V2_4 = 4
        private const val PADDED_DURATION_MS = 1_045L
        private const val DURATION_TOLERANCE_MS = 10L
        private const val MISSING_TRACK_ID = 99
        private const val CHAP_TRACK_ID_OFFSET = 4
        private const val CHPL_COUNT_OFFSET = 12
        private val FREEFORM_ATOMS = listOf("iTunSMPB", "replaygain_track_gain")
    }

    private val files = SampleFiles()

    @Before
    fun setUp() = files.reset()

    @After
    fun tearDown() {
        files.directory.deleteRecursively()
    }

    @Test
    fun aFlacNamedMp3IsNeitherReadNorWritten() {
        val file = files.copy("hires.flac", "hires.mp3.part")

        assertNull(TagLibBridge.read(file.path, "mp3"))
        assertFalse(TagLibBridge.write(file.path, "mp3", mapOf("TITLE" to "x")))
        assertArrayEquals(files.bytes("hires.flac"), file.readBytes())
    }

    @Test
    fun textNamedMp3IsNotAudio() {
        val file = File(files.directory, "notes.mp3.part")
        file.writeText("This is not audio.\n".repeat(NOT_AUDIO_REPEATS))

        assertNull(TagLibBridge.read(file.path, "mp3"))
        assertFalse(TagLibBridge.write(file.path, "mp3", mapOf("TITLE" to "x")))
    }

    @Test
    fun aTruncatedBookReadsAsNothing() {
        val file = File(files.directory, "cut.m4b.part")
        file.writeBytes(files.bytes("book-both.m4b").copyOf(TRUNCATED_BYTES))

        assertNull(TagLibBridge.read(file.path, "m4b"))
        assertFalse(TagLibBridge.write(file.path, "m4b", mapOf("TITLE" to "x")))
    }

    @Test
    fun aMissingFileFailsQuietly() {
        val missing = File(files.directory, "missing.flac").path

        assertNull(TagLibBridge.read(missing, "flac"))
        assertFalse(TagLibBridge.write(missing, "flac", mapOf("TITLE" to "x")))
        assertNull(TagLibBridge.readCover(missing, "flac"))
    }

    @Test
    fun anExtensionHzDoesNotPlayIsRefused() {
        val file = files.copy("hires.flac", "hires.xyz")

        assertNull(TagLibBridge.read(file.path, "xyz"))
        assertFalse(TagLibBridge.write(file.path, "xyz", mapOf("TITLE" to "x")))
    }

    @Test
    fun extensionsMatchInAnyCase() {
        val file = files.copy("hires.flac", "HIRES.FLAC")

        assertNotNull(TagLibBridge.read(file.path, "FLAC"))
    }

    @Test
    fun aBlankValueKeepsTheFilesOwn() {
        val file = files.copy("hires.flac")
        val title = TagLibBridge.read(file.path, "flac")!!.fields["TITLE"]

        TagLibBridge.write(file.path, "flac", mapOf("TITLE" to "  ", "GENRE" to "Jazz"))

        val after = TagLibBridge.read(file.path, "flac")!!.fields
        assertEquals(title, after["TITLE"])
        assertEquals(listOf("Jazz"), after["GENRE"])
    }

    @Test
    fun aValueThatIsNotValidUnicodeKeepsTheFilesOwn() {
        val file = files.copy("hires.flac")
        val title = TagLibBridge.read(file.path, "flac")!!.fields["TITLE"]

        TagLibBridge.write(file.path, "flac", mapOf("TITLE" to "\uD800"))

        assertEquals(title, TagLibBridge.read(file.path, "flac")!!.fields["TITLE"])
    }

    @Test
    fun anMp3WithPaddingAfterItsTagStillOpens() {
        val file = File(files.directory, "padded.mp3.part")
        file.writeBytes(withPaddingAfterTag(files.bytes("id3v24.mp3")))

        val tags = TagLibBridge.read(file.path, "mp3")

        assertNotNull(tags)
        assertEquals("mp3", tags!!.audio.codec)
        val durationMs = tags.audio.durationMs
        assertNotNull("durationMs", durationMs)
        assertTrue(abs(PADDED_DURATION_MS - durationMs!!) <= DURATION_TOLERANCE_MS)
        assertTrue(TagLibBridge.write(file.path, "mp3", mapOf("TITLE" to "x")))
    }

    @Test
    fun writingAnM4aKeepsMixedCaseAtomsSingle() {
        val file = files.copy("aac.m4a")
        val before = String(file.readBytes(), Charsets.ISO_8859_1)
        FREEFORM_ATOMS.forEach { atom -> assertTrue("sample has $atom", before.contains(atom)) }

        assertTrue(TagLibBridge.write(file.path, "m4a", mapOf("TITLE" to "x", "GENRE" to "Jazz")))

        val after = String(file.readBytes(), Charsets.ISO_8859_1).lowercase(Locale.ROOT)
        FREEFORM_ATOMS.forEach { atom ->
            val copies = Regex.fromLiteral(atom.lowercase(Locale.ROOT)).findAll(after).count()
            assertEquals(atom, 1, copies)
        }
    }

    @Test
    fun aChapterTrackPointingAtNoTrackKeepsTheNeroChapters() {
        val bytes = files.bytes("book-both.m4b")
        // tref/chap names the chapter track; 99 names no track.
        val chap = bytes.indexOf("chap", from = bytes.indexOf("tref"))
        ByteBuffer.wrap(bytes).putInt(chap + CHAP_TRACK_ID_OFFSET, MISSING_TRACK_ID)
        val file = File(files.directory, "bad-track.m4b.part").apply { writeBytes(bytes) }

        val tags = TagLibBridge.read(file.path, "m4b")

        assertNotNull(tags)
        assertEquals(SAMPLE_CHAPTERS, tags!!.neroChapters)
        assertEquals(emptyList<Chapter>(), tags.quickTimeChapters)
    }

    @Test
    fun aNeroListClaimingTooManyChaptersStillReads() {
        val bytes = files.bytes("book-both.m4b")
        // After "chpl": version, flags and 4 reserved bytes, then the chapter count.
        bytes[bytes.indexOf("chpl") + CHPL_COUNT_OFFSET] = 0xFF.toByte()
        val file = File(files.directory, "bad-count.m4b.part").apply { writeBytes(bytes) }

        val tags = TagLibBridge.read(file.path, "m4b")

        assertNotNull(tags)
        assertEquals(SAMPLE_CHAPTERS, tags!!.neroChapters)
    }

    @Test
    fun anId3v23TagStays23AndNoId3v1TagIsAdded() {
        val file = files.copy("id3v23.mp3")

        assertTrue(TagLibBridge.write(file.path, "mp3", mapOf("TITLE" to "x")))

        val bytes = file.readBytes()
        assertEquals("ID3", String(bytes, 0, ID3V2_VERSION_AT, Charsets.ISO_8859_1))
        assertEquals(ID3V2_3, bytes[ID3V2_VERSION_AT].toInt())
        assertFalse(bytes.endsWithId3v1Tag())
    }

    @Test
    fun anExistingId3v1TagIsKept() {
        val file = files.copy("with-id3v1.mp3")

        assertTrue(TagLibBridge.write(file.path, "mp3", mapOf("TITLE" to "x")))

        val bytes = file.readBytes()
        assertEquals(ID3V2_4, bytes[ID3V2_VERSION_AT].toInt())
        assertTrue(bytes.endsWithId3v1Tag())
    }

    // 4 KB of zeros between the ID3v2 tag and the audio, which the tag's size doesn't count.
    private fun withPaddingAfterTag(mp3: ByteArray): ByteArray {
        var tagSize = 0
        repeat(SYNCSAFE_BYTES) { index ->
            tagSize = (tagSize shl SYNCSAFE_BITS) or
                (mp3[ID3V2_SIZE_AT + index].toInt() and SYNCSAFE_MASK)
        }
        val audioStart = ID3V2_HEADER_BYTES + tagSize
        return mp3.copyOfRange(0, audioStart) + ByteArray(PADDING_BYTES) +
            mp3.copyOfRange(audioStart, mp3.size)
    }

    private fun ByteArray.endsWithId3v1Tag(): Boolean =
        size >= ID3V1_BYTES && String(this, size - ID3V1_BYTES, 3, Charsets.ISO_8859_1) == "TAG"

    private fun ByteArray.indexOf(text: String, from: Int = 0): Int {
        val pattern = text.toByteArray(Charsets.ISO_8859_1)
        return (from..size - pattern.size).first { start ->
            pattern.indices.all { this[start + it] == pattern[it] }
        }
    }
}
