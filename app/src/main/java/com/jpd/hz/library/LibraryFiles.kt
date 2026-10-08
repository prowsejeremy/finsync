package com.jpd.hz.library

import android.content.Context
import java.io.File

/**
 * Turns the library's saved paths into files (A1): an audio file's or an image's path is relative
 * to the Library folder, and an embedded cover is a file in the cache the scanner writes.
 */
class LibraryFiles(private val libraryFolder: () -> File, private val artCache: File) {

    /** [path], relative to today's Library folder. */
    fun file(path: String): File = File(libraryFolder(), path)

    /** An album's or book's art: the file beside it, else its embedded cover, else null. */
    fun art(path: String?, embedded: String?): String? =
        path?.let { file(it).path } ?: embedded?.let { File(artCache, it).path }

    /** A photo or cover found in the Library folder, or null. */
    fun image(path: String?): String? = path?.let { file(it).path }

    companion object {
        /** In filesDir: the covers found only inside audio files. */
        const val EMBEDDED_ART_FOLDER = "embedded_art"

        fun of(context: Context): LibraryFiles {
            val app = context.applicationContext
            val cache = File(app.filesDir, EMBEDDED_ART_FOLDER)
            return LibraryFiles(LibraryFolderStore(app)::folder, cache)
        }
    }
}
