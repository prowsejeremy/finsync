package com.jpd.hz.adapter.files

import java.io.File

/**
 * Stands in for TagLib, which the JVM can't load. A write appends its fields to the file, even
 * when it reports failure, as a failed TagLib save can leave its file changed.
 */
class FakeTagger(
    private val opens: Boolean = true,
    private val succeeds: Boolean = true
) : FileTagger {

    /** The path and extension of each write, in order. */
    val writes = mutableListOf<Pair<String, String>>()

    override fun canOpen(path: String, extension: String): Boolean = opens

    override fun write(path: String, extension: String, fields: Map<String, String>): Boolean {
        writes.add(path to extension)
        File(path).appendText(fields.toSortedMap().toString())
        return succeeds
    }
}
