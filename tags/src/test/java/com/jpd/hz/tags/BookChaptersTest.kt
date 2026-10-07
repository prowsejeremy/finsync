package com.jpd.hz.tags

import org.junit.Assert.assertEquals
import org.junit.Test

class BookChaptersTest {

    private val nero = listOf(Chapter("Opening", 0L), Chapter("Second part", 1_000L))
    private val quickTime = listOf(Chapter("Intro", 0L), Chapter("Middle", 500L))

    private fun tags(nero: List<Chapter>, quickTime: List<Chapter>) = FileTags(
        fields = emptyMap(),
        audio = AudioDetails(null, null, null, null, null),
        neroChapters = nero,
        quickTimeChapters = quickTime
    )

    @Test
    fun neroChaptersWinWhenAFileHasBoth() {
        assertEquals(nero, BookChapters.of(tags(nero, quickTime)))
    }

    @Test
    fun aQuickTimeChapterTrackIsUsedWhenThereAreNoNeroChapters() {
        assertEquals(quickTime, BookChapters.of(tags(emptyList(), quickTime)))
    }

    @Test
    fun aFileWithNeitherHasNoChapters() {
        assertEquals(emptyList<Chapter>(), BookChapters.of(tags(emptyList(), emptyList())))
    }

    @Test
    fun chaptersComeBackInStartOrder() {
        val shuffled = listOf(Chapter("Two", 2_000L), Chapter("Zero", 0L), Chapter("One", 1_000L))

        assertEquals(
            listOf(Chapter("Zero", 0L), Chapter("One", 1_000L), Chapter("Two", 2_000L)),
            BookChapters.of(tags(shuffled, emptyList()))
        )
    }
}
