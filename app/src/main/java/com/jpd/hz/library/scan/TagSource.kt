package com.jpd.hz.library.scan

import com.jpd.hz.tags.FileTags
import com.jpd.hz.tags.TagLibBridge

/**
 * What the scanner reads from TagLib. Code that unit tests reach takes this interface, never
 * [TagLibBridge]: touching the bridge loads libhztags.so, which the JVM doesn't have.
 */
interface TagSource {

    /** Null when TagLib can't open the file as the format [extension] names. */
    fun read(path: String, extension: String): FileTags?

    /** The front cover's bytes, else the first picture's, else null. */
    fun readCover(path: String, extension: String): ByteArray?
}

/** The real reader. The scanner's factory passes it in at the edge. */
object TagLibSource : TagSource {

    override fun read(path: String, extension: String): FileTags? =
        TagLibBridge.read(path, extension)

    override fun readCover(path: String, extension: String): ByteArray? =
        TagLibBridge.readCover(path, extension)
}

/** Whether BASS can play a file TagLib couldn't open (spec "Errors"). */
fun interface DecodeCheck {
    /** Null when BASS itself couldn't start, so the file is tried again next pass. */
    fun canDecode(path: String): Boolean?
}
