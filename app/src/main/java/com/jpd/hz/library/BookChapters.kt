package com.jpd.hz.library

/** One of a book's chapters: its name and where it starts in the book. */
data class Chapter(val name: String, val startMs: Long)

/**
 * The chapters to show and play: the file's, or, when it has none, one chapter named after the
 * book that starts at 0 (3b spec "No chapters from the server").
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

// More than this into a chapter, previous chapter restarts it (spec "Previous chapter").
private const val RESTART_AFTER_MS = 3_000L

/**
 * Previous chapter: this chapter's start once more than 3 s of it has played, else the previous
 * chapter's start. From the first chapter, that's the start of the book.
 */
fun previousChapterTarget(startsMs: List<Long>, positionMs: Long): Long {
    if (startsMs.isEmpty()) return 0L
    val index = currentChapterIndex(startsMs, positionMs)
    val start = startsMs[index]
    return when {
        positionMs - start > RESTART_AFTER_MS -> start
        index > 0 -> startsMs[index - 1]
        else -> 0L
    }
}

/** Next chapter's start, or null on the last chapter, where next does nothing (spec). */
fun nextChapterTarget(startsMs: List<Long>, positionMs: Long): Long? =
    startsMs.firstOrNull { it > positionMs }

/** [positionMs] moved by [deltaMs], kept within the book; an unknown length (0) has no end. */
fun skipTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val end = if (durationMs > 0L) durationMs else Long.MAX_VALUE
    return (positionMs + deltaMs).coerceIn(0L, end)
}

/** The span the Player's seek bar covers while a book plays: the current chapter. */
data class ChapterWindow(val index: Int, val startMs: Long, val endMs: Long) {
    val lengthMs: Long get() = endMs - startMs
}

fun chapterWindowAt(startsMs: List<Long>, positionMs: Long, durationMs: Long): ChapterWindow {
    val index = currentChapterIndex(startsMs, positionMs)
    val start = startsMs.getOrElse(index) { 0L }
    val end = startsMs.getOrNull(index + 1) ?: durationMs
    return ChapterWindow(index, start, end.coerceAtLeast(start))
}
