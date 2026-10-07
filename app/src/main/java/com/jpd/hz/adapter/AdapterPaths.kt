package com.jpd.hz.adapter

private val UNSAFE_CHARACTERS = Regex("[/\\\\:*?\"<>|]")

/**
 * A server, artist, album, file or playlist name made safe as one file or folder name. Moved
 * unchanged from sync/SyncPaths.kt, because every adapter's paths use it (D12).
 */
internal fun sanitizeFilename(name: String): String = name.replace(UNSAFE_CHARACTERS, "_").trim()

/**
 * A name that's safe as a visible folder or file of its own: sanitised, with leading dots
 * dropped so it's never hidden or `..`. Blank results become [fallback].
 */
internal fun visibleName(name: String, fallback: String): String =
    sanitizeFilename(name).trimStart('.').trim().ifEmpty { fallback }
