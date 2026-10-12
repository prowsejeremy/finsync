package com.jpd.hz.library.scan

import android.system.ErrnoException
import android.system.Os
import java.io.File

/**
 * What says whether a file changed since the last scan: its size, last-modified and
 * last-changed times (whole seconds) and its inode. The change time is there because T2's phone
 * check saw a re-tag keep a file's size and last-modified time; only ctime moved.
 */
data class FileStamp(val size: Long, val modifiedSec: Long, val changedSec: Long, val inode: Long)

/** Reads a file's stamp, or null when it can't. Unit tests pass a fake: Os needs Android. */
fun interface FileStamper {
    fun stampOf(file: File): FileStamp?
}

/** One stat per file, which the phone spike timed at 41 ms for 1,477 files. */
object OsStamper : FileStamper {
    override fun stampOf(file: File): FileStamp? = try {
        val stat = Os.stat(file.path)
        FileStamp(stat.st_size, stat.st_mtime, stat.st_ctime, stat.st_ino)
    } catch (e: ErrnoException) {
        // Gone since the walk, or unreadable: it's left out of this pass.
        null
    }
}
