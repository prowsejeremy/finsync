package com.jpd.hz.adapter

import java.io.File
import java.io.IOException

private const val PART_SUFFIX = ".part"
// A 326 MB book copies faster with a bigger buffer than copyTo's 8 KB default.
private const val COPY_BUFFER_BYTES = 1 shl 20

/** How tagging one file went. */
enum class TagResult {
    /** Our fields are in the file. */
    TAGGED,

    /** TagLib can't open the file as its format, so nothing was written. It plays as it is. */
    UNREADABLE,

    /** The write or the rename failed. The ".part" file was deleted. */
    FAILED
}

/**
 * The file steps every adapter shares (D12). TagLib only ever writes to a ".part" file, because
 * a failed write can leave its file changed. Each step renames the ".part" file into place last.
 */
object AdapterFiles {

    /** Where [file] is downloaded or copied to before it's renamed into place. */
    fun partOf(file: File): File = File(file.path + PART_SUFFIX)

    /**
     * Tags the finished download at [part], then renames it to [target]. UNREADABLE still
     * renames it, so the file plays with its own tags. FAILED leaves no ".part" file, and the
     * caller downloads again.
     */
    fun finishDownload(
        part: File,
        target: File,
        fields: Map<String, String>,
        tagger: FileTagger
    ): TagResult {
        val result = tag(part, target.extension, fields, tagger)
        if (result == TagResult.FAILED || !part.renameTo(target)) {
            part.delete()
            return TagResult.FAILED
        }
        return result
    }

    /**
     * Re-tags [file] through a copy: copies it to its ".part" file, tags the copy and renames it
     * over [file]. Anything but TAGGED leaves [file] as it was. An interrupted re-tag leaves only
     * the ".part" file, which cleanup removes. Throws when the copy fails.
     */
    fun retag(file: File, fields: Map<String, String>, tagger: FileTagger): TagResult {
        if (fields.isEmpty()) return TagResult.TAGGED
        val extension = file.extension
        // Checked on the original, so a file TagLib can't open isn't copied for nothing.
        if (!tagger.canOpen(file.path, extension)) return TagResult.UNREADABLE
        val part = partOf(file)
        try {
            file.copyTo(part, overwrite = true, bufferSize = COPY_BUFFER_BYTES)
            if (tagger.write(part.path, extension, fields) && part.renameTo(file)) {
                return TagResult.TAGGED
            }
        } finally {
            // After a rename there's nothing left to delete.
            part.delete()
        }
        return TagResult.FAILED
    }

    /**
     * Copies [source] to [target] through a ".part" file, unless [target] already has its size.
     * Sync only fetches a photo or cover once, so a changed one with the same size is rare.
     */
    fun copyIfChanged(source: File, target: File) {
        if (target.isFile && target.length() == source.length()) return
        val part = partOf(target)
        try {
            source.copyTo(part, overwrite = true, bufferSize = COPY_BUFFER_BYTES)
            renameOrThrow(part, target)
        } finally {
            part.delete()
        }
    }

    /** Writes [text] as UTF-8 with no byte-order mark, unless [target] already holds it. */
    fun writeIfChanged(target: File, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (target.isFile && target.length() == bytes.size.toLong() &&
            target.readBytes().contentEquals(bytes)
        ) {
            return
        }
        target.parentFile?.mkdirs()
        val part = partOf(target)
        try {
            part.writeBytes(bytes)
            renameOrThrow(part, target)
        } finally {
            part.delete()
        }
    }

    private fun tag(
        part: File,
        extension: String,
        fields: Map<String, String>,
        tagger: FileTagger
    ): TagResult = when {
        fields.isEmpty() -> TagResult.TAGGED
        !tagger.canOpen(part.path, extension) -> TagResult.UNREADABLE
        tagger.write(part.path, extension, fields) -> TagResult.TAGGED
        else -> TagResult.FAILED
    }

    private fun renameOrThrow(part: File, target: File) {
        if (!part.renameTo(target)) throw IOException("Couldn't rename ${part.name}")
    }
}
