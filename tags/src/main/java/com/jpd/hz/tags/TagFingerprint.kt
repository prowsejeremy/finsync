package com.jpd.hz.tags

import java.security.MessageDigest

private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private const val HEX_DIGITS_PER_BYTE = 2

/**
 * Identifies the fields an adapter wrote to a file. A later sync whose fields give a different
 * fingerprint re-tags the file without downloading it again.
 */
object TagFingerprint {
    /** SHA-256, as lowercase hex, of the fields as "FIELD=value" lines sorted by field name. */
    fun of(fields: Map<String, String>): String {
        val lines = fields.toSortedMap().entries
            .joinToString("\n") { (field, value) -> "$field=$value" }
        return MessageDigest.getInstance("SHA-256")
            .digest(lines.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->
                (byte.toInt() and BYTE_MASK).toString(HEX_RADIX).padStart(HEX_DIGITS_PER_BYTE, '0')
            }
    }
}
