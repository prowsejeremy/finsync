package com.jpd.hz.tags

/**
 * Kotlin's door to TagLib, through libhztags.so. [extension] is the file's real format without
 * the dot, in any case, so a ".part" download can be read and written before it's renamed.
 */
object TagLibBridge {
    init {
        System.loadLibrary("hztags")
    }

    /** Null when the file is missing, or isn't the format its extension names. */
    fun read(path: String, extension: String): FileTags? =
        nativeRead(path, extension)?.let(NativeTags::decode)

    /**
     * Replaces the given fields, by TagLib property name, and keeps every other tag. Blank values
     * are skipped, so the file keeps its own (D2). False on failure, which can leave the file
     * changed, so callers write only to a ".part" copy.
     */
    fun write(path: String, extension: String, fields: Map<String, String>): Boolean =
        nativeWrite(path, extension, NativeTags.encode(fields.filterValues { it.isNotBlank() }))

    /** The front cover's bytes, else the first picture's, else null. */
    fun readCover(path: String, extension: String): ByteArray? = nativeReadCover(path, extension)

    private external fun nativeRead(path: String, extension: String): Array<String?>?

    private external fun nativeWrite(
        path: String,
        extension: String,
        keysAndValues: Array<String>
    ): Boolean

    private external fun nativeReadCover(path: String, extension: String): ByteArray?
}
