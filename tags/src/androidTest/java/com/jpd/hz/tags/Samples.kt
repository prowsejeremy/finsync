package com.jpd.hz.tags

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Which chapter formats a sample carries. */
enum class ChapterFormats { NONE, BOTH, NERO, QUICK_TIME }

/**
 * One file in assets/samples, with the audio details TagLib reported for it on the Mac when the
 * samples were made. tags/src/androidTest/SAMPLES.md says how each one was made.
 */
data class Sample(
    val name: String,
    val codec: String,
    val durationMs: Long,
    val sampleRate: Int,
    val bitDepth: Int?,
    val bitrate: Int,
    val cover: String?,
    val chapters: ChapterFormats = ChapterFormats.NONE
) {
    val extension: String get() = name.substringAfterLast('.')

    val isBook: Boolean get() = extension == "m4b"

    // Parameterized test names show this.
    override fun toString(): String = name
}

const val FRONT_COVER = "front.jpg"
const val BACK_COVER = "back.png"

val SAMPLES = listOf(
    Sample("id3v24.mp3", "mp3", 1045, 44100, null, 131_000, FRONT_COVER),
    Sample("id3v23.mp3", "mp3", 1045, 44100, null, 131_000, FRONT_COVER),
    Sample("with-id3v1.mp3", "mp3", 1045, 44100, null, 131_000, null),
    Sample("hires.flac", "flac", 1000, 44100, 24, 124_000, FRONT_COVER),
    Sample("aac.m4a", "aac", 1023, 44100, null, 116_000, FRONT_COVER),
    Sample("alac.m4a", "alac", 1000, 44100, 16, 706_000, FRONT_COVER),
    // MP4 pictures have no type, so the first one, the back cover, is the one returned.
    Sample("book-both.m4b", "aac", 2023, 44100, null, 65_000, BACK_COVER, ChapterFormats.BOTH),
    Sample("book-nero.m4b", "aac", 2023, 44100, null, 65_000, null, ChapterFormats.NERO),
    Sample("book-quicktime.m4b", "aac", 2023, 44100, null, 65_000, null, ChapterFormats.QUICK_TIME),
    Sample("vorbis.ogg", "vorbis", 1001, 44100, null, 32_000, FRONT_COVER),
    Sample("opus.opus", "opus", 1000, 48000, null, 122_000, FRONT_COVER),
    Sample("wavpack.wv", "wavpack", 1000, 44100, 16, 185_000, FRONT_COVER),
    Sample("pcm.wav", "pcm", 1000, 44100, 16, 706_000, FRONT_COVER),
    Sample("pcm.aiff", "pcm", 1000, 44100, 16, 706_000, FRONT_COVER),
    Sample("monkey.ape", "ape", 3550, 44100, 16, 192_000, FRONT_COVER)
)

/** Both chapter formats in the book samples hold these two chapters. */
val SAMPLE_CHAPTERS = listOf(Chapter("Opening", 0L), Chapter("Second part", 1_000L))

/** Copies samples out of the test APK into a folder where TagLib can open them by path. */
class SampleFiles {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    val directory = File(instrumentation.targetContext.cacheDir, "tag-samples")

    fun reset() {
        directory.deleteRecursively()
        directory.mkdirs()
    }

    fun bytes(asset: String): ByteArray =
        instrumentation.context.assets.open("samples/$asset").use { it.readBytes() }

    /** Named "<asset>.part" unless [name] says otherwise, as a download in progress is. */
    fun copy(asset: String, name: String = "$asset.part"): File =
        File(directory, name).apply { writeBytes(bytes(asset)) }
}
