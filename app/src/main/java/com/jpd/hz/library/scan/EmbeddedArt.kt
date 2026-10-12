package com.jpd.hz.library.scan

import java.io.File
import java.io.IOException
import java.security.MessageDigest

private const val PART_SUFFIX = ".part"
private const val HASH = "SHA-1"

/**
 * Covers found only inside audio files (spec "Embedded-art cache"), one file each in [folder],
 * which is filesDir/embedded_art. The bytes are written as found, because Glide and Media3 decode
 * them by content, not by extension. An empty file records that the source has no cover, so it
 * isn't read again until the source changes.
 */
class EmbeddedArt(private val folder: File, private val tags: TagSource) {

    // The cache files the current pass asked for, "no cover" records included.
    private val used = HashSet<String>()

    /** Starts a pass: no cache file is in use until [coverFor] asks for it. */
    fun startPass() {
        used.clear()
    }

    /**
     * The cover of album or book [id] from the audio file [source], at [path] in the Library
     * folder. It's read again when [changed], meaning the source was re-read this pass. The cache
     * file is named after the ID and the source's path, so a new first track gets its own file.
     * Null when the source has no cover or it can't be saved.
     */
    fun coverFor(id: String, path: String, source: File, changed: Boolean): File? {
        val target = File(folder, cacheName(id, path))
        used.add(target.name)
        if (changed || !target.isFile) {
            val bytes = tags.readCover(source.path, ScanRules.extensionOf(source.name))
            if (!save(target, bytes ?: ByteArray(0))) return null
        }
        return target.takeIf { it.length() > 0 }
    }

    /** Deletes every cache file this pass didn't ask for, once the pass has written the library. */
    fun deleteUnused() {
        folder.listFiles()?.forEach { file ->
            if (file.name !in used) file.delete()
        }
    }

    // Written beside the target and renamed last, so a half-written cover never shows.
    private fun save(target: File, bytes: ByteArray): Boolean {
        val part = File(target.path + PART_SUFFIX)
        return try {
            folder.mkdirs()
            part.writeBytes(bytes)
            part.renameTo(target)
        } catch (e: IOException) {
            false
        } finally {
            part.delete()
        }
    }

    private fun cacheName(id: String, path: String): String =
        MessageDigest.getInstance(HASH)
            .digest("$id\n$path".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
