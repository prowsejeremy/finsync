package com.jpd.hz.tags

/**
 * The flat string array the native read returns (built by `slotsOf` in hz_tags_jni.cpp), so JNI
 * only ever passes strings:
 * - slots 0–4: codec, duration in ms, sample rate, bit depth and bitrate, each null if unknown;
 * - a count of field values, then that many key and value pairs;
 * - a count of Nero chapters, then that many name and start pairs;
 * - a count of QuickTime chapters, then the same.
 */
internal object NativeTags {
    private const val CODEC = 0
    private const val DURATION_MS = 1
    private const val SAMPLE_RATE = 2
    private const val BIT_DEPTH = 3
    private const val BITRATE = 4
    private const val FIRST_SECTION = 5

    fun decode(slots: Array<String?>): FileTags {
        val reader = SlotReader(slots, FIRST_SECTION)
        val fields = LinkedHashMap<String, MutableList<String>>()
        repeat(reader.count()) {
            val key = reader.text()
            fields.getOrPut(key) { mutableListOf() }.add(reader.text())
        }
        return FileTags(
            fields = fields,
            audio = AudioDetails(
                durationMs = slots[DURATION_MS]?.toLongOrNull(),
                sampleRate = slots[SAMPLE_RATE]?.toIntOrNull(),
                bitDepth = slots[BIT_DEPTH]?.toIntOrNull(),
                bitrate = slots[BITRATE]?.toIntOrNull(),
                codec = slots[CODEC]
            ),
            neroChapters = reader.chapters(),
            quickTimeChapters = reader.chapters()
        )
    }

    /** Flattens fields into key and value pairs for the native write. */
    fun encode(fields: Map<String, String>): Array<String> =
        fields.flatMap { (key, value) -> listOf(key, value) }.toTypedArray()

    private class SlotReader(private val slots: Array<String?>, private var next: Int) {
        fun text(): String = slots[next++].orEmpty()

        fun count(): Int = text().toInt()

        fun chapters(): List<Chapter> =
            List(count()) { Chapter(name = text(), startMs = text().toLong()) }
    }
}
