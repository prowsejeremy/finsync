package com.jpd.hz.tags

object BookChapters {
    /**
     * A book's chapters, in start order. Nero chapters win over a QuickTime chapter track when a
     * file has both, and a file with neither has none.
     */
    fun of(tags: FileTags): List<Chapter> =
        tags.neroChapters.ifEmpty { tags.quickTimeChapters }.sortedBy(Chapter::startMs)
}
