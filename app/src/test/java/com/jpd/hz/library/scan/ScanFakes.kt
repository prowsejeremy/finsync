package com.jpd.hz.library.scan

import com.jpd.hz.tags.AudioDetails
import com.jpd.hz.tags.Chapter
import com.jpd.hz.tags.FileTags
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

/** TagLib's reads by file name, so a moved file keeps its tags. Counts every read. */
class FakeTagSource : TagSource {
    val tagsByName = HashMap<String, FileTags>()
    val coversByName = HashMap<String, ByteArray>()
    /** Reading one of these throws, as a JNI failure would. */
    val failingNames = HashSet<String>()
    private val readPaths = ArrayList<String>()
    private val coverPaths = ArrayList<String>()

    val reads: List<String> get() = synchronized(readPaths) { readPaths.toList() }
    val coverReads: List<String> get() = synchronized(coverPaths) { coverPaths.toList() }

    override fun read(path: String, extension: String): FileTags? {
        synchronized(readPaths) { readPaths.add(path) }
        if (File(path).name in failingNames) throw IllegalStateException("TagLib failed")
        return tagsByName[File(path).name]
    }

    override fun readCover(path: String, extension: String): ByteArray? {
        synchronized(coverPaths) { coverPaths.add(path) }
        return coversByName[File(path).name]
    }
}

fun tagsOf(
    vararg fields: Pair<String, String>,
    durationMs: Long? = 1_000L,
    codec: String = "mp3",
    chapters: List<Chapter> = emptyList()
): FileTags = FileTags(
    fields = fields.groupBy({ it.first }, { it.second }),
    audio = AudioDetails(durationMs, 44_100, null, 128_000, codec),
    neroChapters = emptyList(),
    quickTimeChapters = chapters
)

/** Real stamps from the host's file system, through Java's unix attribute view. */
object JvmStamper : FileStamper {
    override fun stampOf(file: File): FileStamp? = try {
        val attributes = Files.readAttributes(file.toPath(), "unix:size,lastModifiedTime,ctime,ino")
        FileStamp(
            size = attributes["size"] as Long,
            modifiedSec = (attributes["lastModifiedTime"] as FileTime).to(TimeUnit.SECONDS),
            changedSec = (attributes["ctime"] as FileTime).to(TimeUnit.SECONDS),
            inode = attributes["ino"] as Long
        )
    } catch (e: IOException) {
        null
    }
}
