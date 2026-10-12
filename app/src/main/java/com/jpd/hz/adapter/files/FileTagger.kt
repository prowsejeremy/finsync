package com.jpd.hz.adapter.files

import com.jpd.hz.tags.TagLibBridge

/**
 * What adapters need from TagLib. Code that unit tests reach takes this interface, never
 * [TagLibBridge]: touching the bridge loads libhztags.so, which the JVM doesn't have.
 */
interface FileTagger {

    /** True when TagLib opens the file as the format [extension] names. */
    fun canOpen(path: String, extension: String): Boolean

    /** Writes only [fields]. False on failure, which can leave the file changed. */
    fun write(path: String, extension: String, fields: Map<String, String>): Boolean
}

/** The real tagger, through TagLib. Sync passes it in at the edge. */
object TagLibTagger : FileTagger {

    override fun canOpen(path: String, extension: String): Boolean =
        TagLibBridge.read(path, extension) != null

    override fun write(path: String, extension: String, fields: Map<String, String>): Boolean =
        TagLibBridge.write(path, extension, fields)
}
