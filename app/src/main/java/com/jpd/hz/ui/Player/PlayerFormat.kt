package com.jpd.hz.ui

import java.util.Locale

private const val HZ_PER_KHZ = 1_000.0
private const val TENTHS_PER_UNIT = 10L
private const val BITS_PER_KBIT = 1_000.0
private const val BYTES_PER_MB = 1_000_000.0
private const val PERMILLE = 1_000L

/**
 * The Player's info row, e.g. "FLAC · 16-bit · 44.1 kHz · 833 kbps · 63.6 MB". Missing parts
 * are left out; with none at all the result is empty and the row hides.
 */
fun formatInfoRow(info: TrackInfo): String = joinWithDots(
    listOf(
        info.codec?.uppercase(Locale.ROOT),
        info.bitDepth?.let { "$it-bit" },
        info.sampleRate?.let(::formatKilohertz),
        info.bitrate?.let { "${Math.round(it / BITS_PER_KBIT)} kbps" },
        info.sizeBytes?.let { String.format(Locale.ROOT, "%.1f MB", it / BYTES_PER_MB) }
    )
)

/** Progress in thousandths of the track, for the mini-player's line. */
fun progressPermille(positionMs: Long, durationMs: Long): Int {
    if (durationMs <= 0L) return 0
    return (positionMs * PERMILLE / durationMs).coerceIn(0L, PERMILLE).toInt()
}

// At most one decimal, and none when it's whole: 44100 → "44.1 kHz", 48000 → "48 kHz".
private fun formatKilohertz(sampleRate: Int): String {
    val tenths = Math.round(sampleRate / HZ_PER_KHZ * TENTHS_PER_UNIT)
    val whole = tenths / TENTHS_PER_UNIT
    val decimal = tenths % TENTHS_PER_UNIT
    return if (decimal == 0L) "$whole kHz" else "$whole.$decimal kHz"
}
