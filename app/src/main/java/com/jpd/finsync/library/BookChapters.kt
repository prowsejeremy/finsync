package com.jpd.finsync.library

/** One of a book's chapters: its name and where it starts in the book. */
data class Chapter(val name: String, val startMs: Long)

/**
 * The chapters to show and play: the server's, or, when it sends none, one chapter named after
 * the book that starts at 0 (spec "No chapters from the server").
 */
fun chaptersOrWhole(chapters: List<Chapter>, bookName: String): List<Chapter> =
    chapters.ifEmpty { listOf(Chapter(bookName, 0L)) }

/**
 * The current chapter: the last one starting at or before [positionMs]. Before the first
 * chapter's start, that's the first chapter (spec "Current chapter").
 */
fun currentChapterIndex(startsMs: List<Long>, positionMs: Long): Int =
    startsMs.indexOfLast { it <= positionMs }.coerceAtLeast(0)

/** Each chapter's length: to the next chapter's start, and the last to the end of the book. */
fun chapterLengthsMs(startsMs: List<Long>, durationMs: Long): List<Long> =
    startsMs.mapIndexed { index, start ->
        val end = startsMs.getOrNull(index + 1) ?: durationMs
        (end - start).coerceAtLeast(0L)
    }
