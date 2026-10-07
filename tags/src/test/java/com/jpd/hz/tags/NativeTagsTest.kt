package com.jpd.hz.tags

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeTagsTest {

    @Test
    fun decodesTheAudioDetailsFieldsAndChapters() {
        val slots = arrayOf<String?>(
            "flac", "2000", "44100", "24", "123000",
            "3", "ARTIST", "A", "ARTIST", "B", "TITLE", "T",
            "1", "Intro", "0",
            "2", "One", "0", "Two", "1500"
        )

        val tags = NativeTags.decode(slots)

        assertEquals(mapOf("ARTIST" to listOf("A", "B"), "TITLE" to listOf("T")), tags.fields)
        assertEquals(AudioDetails(2000L, 44100, 24, 123000, "flac"), tags.audio)
        assertEquals(listOf(Chapter("Intro", 0L)), tags.neroChapters)
        assertEquals(listOf(Chapter("One", 0L), Chapter("Two", 1500L)), tags.quickTimeChapters)
    }

    @Test
    fun unknownAudioDetailsAreNull() {
        val slots = arrayOf<String?>(null, null, null, null, null, "0", "0", "0")

        val tags = NativeTags.decode(slots)

        assertEquals(AudioDetails(null, null, null, null, null), tags.audio)
        assertEquals(emptyMap<String, List<String>>(), tags.fields)
    }

    @Test
    fun encodesFieldsAsKeyValuePairs() {
        val encoded = NativeTags.encode(mapOf("TITLE" to "T", "GENRE" to "Rock; Pop"))

        assertArrayEquals(arrayOf("TITLE", "T", "GENRE", "Rock; Pop"), encoded)
    }
}
