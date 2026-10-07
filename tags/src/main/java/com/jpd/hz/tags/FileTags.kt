package com.jpd.hz.tags

/**
 * What TagLib read from one file. [fields] holds every text field under TagLib's property names
 * ("ALBUMARTIST"), with a native multi-value field as several values. Only MP4 files have
 * chapters, in either or both of two formats.
 */
data class FileTags(
    val fields: Map<String, List<String>>,
    val audio: AudioDetails,
    val neroChapters: List<Chapter>,
    val quickTimeChapters: List<Chapter>
)

/**
 * Units match the app's existing audio details: milliseconds, hertz and bits per second. Codec
 * names are lowercase ("flac", "aac", "alac"). Bit depth is only known for lossless audio.
 */
data class AudioDetails(
    val durationMs: Long?,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val bitrate: Int?,
    val codec: String?
)

data class Chapter(val name: String, val startMs: Long)
