package com.jpd.hz.platform.jellyfin

import org.junit.Assert.assertEquals

/**
 * The golden files' text (adapter harness spec, "Testing" 1). Every output is in plain lines, so a
 * difference shows exactly what changed.
 */
object GoldenText {

    /** One planned item: its ID, its path in the adapter's folder, and the tags sync writes. */
    data class PlannedFile(
        val itemId: String,
        val path: String,
        val fingerprint: String?,
        val fields: Map<String, String>
    )

    fun render(
        planned: List<PlannedFile>,
        keep: Collection<String>,
        playlistFiles: Map<String, String>,
        artistPhotos: Map<String, String>
    ): String = buildString {
        appendLine("# plan: item, path, fingerprint")
        planned.forEach { appendLine("${it.itemId}\t${it.path}\t${it.fingerprint}") }
        appendLine("# fields")
        planned.forEach { file ->
            val fields = file.fields.toSortedMap().entries.joinToString(" | ") { (key, value) ->
                "$key=$value"
            }
            appendLine("${file.itemId}\t$fields")
        }
        appendLine("# keep")
        keep.sorted().forEach(::appendLine)
        appendLine("# artist photos: path, artist")
        artistPhotos.toSortedMap().forEach { (path, artistId) -> appendLine("$path\t$artistId") }
        playlistFiles.toSortedMap().forEach { (path, text) ->
            appendLine("# playlist file $path")
            append(text)
        }
    }

    /** Compares [actual] with the golden file at [resource] on the test classpath. */
    fun assertMatches(resource: String, actual: String) {
        val stream = javaClass.classLoader?.getResourceAsStream(resource)
            ?: error("Missing golden file $resource")
        val expected = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals("Golden file $resource", expected, actual)
    }
}
