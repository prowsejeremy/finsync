package com.jpd.hz.library

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private const val PREFS = "settings"
private const val KEY = "library_folder"
// The default Library folder, in public storage or else in the app's external files folder.
private const val DEFAULT_FOLDER = "Media/hz"

/**
 * The `library_folder` setting: the folder the player reads (spec "Saved settings"). Until one is
 * saved, it's public Media/hz, or Media/hz in the app's external files folder when public storage
 * can't be written. Media isn't one of Android's standard folders, so writing it needs all-files
 * access.
 */
class LibraryFolderStore(context: Context) {

    companion object {
        // Bumped around every change that moves files inside the Library folder, so a scan that
        // ran across one throws its result away and runs again (LibraryScanner).
        private val folderChanges = AtomicInteger()

        /** Call before and after moving files inside the Library folder. */
        fun noteFolderChange() {
            folderChanges.incrementAndGet()
        }

        fun folderChanges(): Int = folderChanges.get()
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saved(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    /** Written at once, not in the background: a folder move relies on it (T3). */
    fun save(path: String) {
        prefs.edit().putString(KEY, path).commit()
    }

    /** The saved folder, else the default. Nothing is saved here. */
    fun folder(): File = saved()?.let(::File) ?: defaultFolder()

    fun defaultFolder(): File = publicDefault() ?: appDefault()

    /** Public Media/hz, or null when it can't be written. */
    fun publicDefault(): File? {
        val folder = File(Environment.getExternalStorageDirectory(), DEFAULT_FOLDER)
        folder.mkdirs()
        return folder.takeIf { it.isDirectory && it.canWrite() }
    }

    fun appDefault(): File = File(appContext.getExternalFilesDir(null), DEFAULT_FOLDER)

    /** The folder as screens name it: "Media/hz" rather than its whole path. */
    fun displayPath(): String = displayPathOf(folder().path)

    /** [path] as screens name it, from shared storage's top. */
    fun displayPathOf(path: String): String =
        displayPathOf(path, Environment.getExternalStorageDirectory().path)

    /** [path] named from the Library folder when it's inside it, such as "kurage". */
    fun nameInLibrary(path: String): String =
        nameInLibraryOf(path, folder().path) ?: displayPathOf(path)
}

/** [path] inside [library] from there, or null when it's elsewhere. */
internal fun nameInLibraryOf(path: String, library: String): String? {
    val root = library.trimEnd('/')
    return if (path.startsWith("$root/")) path.substring(root.length + 1) else null
}

/** [path] inside [storageRoot] loses the root's part; any other path stays whole. */
internal fun displayPathOf(path: String, storageRoot: String): String {
    val root = storageRoot.trimEnd('/')
    return if (path.startsWith("$root/")) path.substring(root.length + 1) else path
}
