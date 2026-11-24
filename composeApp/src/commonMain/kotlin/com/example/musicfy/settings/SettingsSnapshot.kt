// SettingsSnapshot.kt
//
// Typed codec for `SettingEntry`, the value type used by SettingsStore.snapshot()
// and by the transfer JSON. This is deliberately external to SettingsStore:
// the store itself only exposes b/i/l/f/s, while transfer archives also carry
// d/s/ss/bytes.

package com.example.musicfy.settings

/**
 * A SettingEntry as it appears on the wire: [v] is a JSON primitive/list, i.e.
 * Floats are widened to Double, string sets to List<String> and byte arrays to
 * unpadded-free Base64 text (the same shape Android's TransferArchive writes).
 */
data class EncodedSetting(val t: String, val v: Any)

object SettingsSnapshot {
    const val TYPE_BOOLEAN = "b"
    const val TYPE_INT = "i"
    const val TYPE_LONG = "l"
    const val TYPE_FLOAT = "f"
    const val TYPE_DOUBLE = "d"
    const val TYPE_STRING = "s"
    const val TYPE_STRING_SET = "ss"
    const val TYPE_BYTES = "bytes"

    val KnownTypes = setOf(
        TYPE_BOOLEAN, TYPE_INT, TYPE_LONG, TYPE_FLOAT,
        TYPE_DOUBLE, TYPE_STRING, TYPE_STRING_SET, TYPE_BYTES,
    )

    /** Normalizes a snapshot entry, dropping malformed or unknown values. */
    fun normalize(entry: SettingEntry): SettingEntry? = decode(entry.t, entry.v)

    /**
     * Converts a snapshot entry to a JSON-ready value. Bytes are Base64 text,
     * sets become lists, and Floats become Doubles (upstream does the same).
     */
    fun encode(entry: SettingEntry): EncodedSetting? {
        val value = when (entry.t) {
            TYPE_BOOLEAN -> entry.v as? Boolean ?: return null
            TYPE_INT -> (entry.v as? Number)?.toInt() ?: return null
            TYPE_LONG -> (entry.v as? Number)?.toLong() ?: return null
            TYPE_FLOAT -> (entry.v as? Number)?.toFloat()?.toDouble() ?: return null
            TYPE_DOUBLE -> (entry.v as? Number)?.toDouble() ?: return null
            TYPE_STRING -> entry.v as? String ?: return null
            TYPE_STRING_SET -> when (val v = entry.v) {
                is Collection<*> -> v.filterIsInstance<String>().toList()
                is Array<*> -> v.filterIsInstance<String>().toList()
                else -> return null
            }
            TYPE_BYTES -> when (val v = entry.v) {
                is ByteArray -> encodeBase64(v)
                is String -> v // already encoded
                else -> return null
            }
            else -> return null
        }
        return EncodedSetting(entry.t, value)
    }

    /** Inverse of [encode]; returns null for unknown types or bad values. */
    fun decode(type: String, value: Any?): SettingEntry? {
        val decoded = when (type) {
            TYPE_BOOLEAN -> value as? Boolean ?: return null
            TYPE_INT -> (value as? Number)?.toInt() ?: return null
            TYPE_LONG -> (value as? Number)?.toLong() ?: return null
            TYPE_FLOAT -> (value as? Number)?.toFloat() ?: return null
            TYPE_DOUBLE -> (value as? Number)?.toDouble() ?: return null
            TYPE_STRING -> value as? String ?: return null
            TYPE_STRING_SET -> when (value) {
                is Collection<*> -> value.filterIsInstance<String>().toSet()
                is Array<*> -> value.filterIsInstance<String>().toSet()
                else -> return null
            }
            TYPE_BYTES -> when (value) {
                is ByteArray -> value
                is String -> decodeBase64(value) ?: return null
                else -> return null
            }
            else -> return null
        }
        return SettingEntry(type, decoded)
    }

    fun asBoolean(entry: SettingEntry?, default: Boolean = false): Boolean =
        entry?.v as? Boolean ?: default

    fun asInt(entry: SettingEntry?, default: Int = 0): Int =
        (entry?.v as? Number)?.toInt() ?: default

    fun asLong(entry: SettingEntry?, default: Long = 0L): Long =
        (entry?.v as? Number)?.toLong() ?: default

    fun asFloat(entry: SettingEntry?, default: Float = 0f): Float =
        (entry?.v as? Number)?.toFloat() ?: default

    fun asDouble(entry: SettingEntry?, default: Double = 0.0): Double =
        (entry?.v as? Number)?.toDouble() ?: default

    fun asString(entry: SettingEntry?, default: String = ""): String =
        entry?.v as? String ?: default

    fun asStringSet(entry: SettingEntry?, default: Set<String> = emptySet()): Set<String> =
        when (val v = entry?.v) {
            is Collection<*> -> v.filterIsInstance<String>().toSet()
            is Array<*> -> v.filterIsInstance<String>().toSet()
            else -> default
        }

    fun asBytes(entry: SettingEntry?, default: ByteArray = ByteArray(0)): ByteArray =
        when (val v = entry?.v) {
            is ByteArray -> v
            is String -> decodeBase64(v) ?: default
            else -> default
        }

    // Standalone Base64 so no platform Base64 API (android.util.Base64 /
    // Foundation) is needed in common code, and no opt-in is required.

    private const val Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    private const val Pad = '='

    fun encodeBase64(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i + 2 < bytes.size) {
            val bits = (bytes[i].toInt() and 0xff shl 16) or
                (bytes[i + 1].toInt() and 0xff shl 8) or
                (bytes[i + 2].toInt() and 0xff)
            appendTriple(out, bits)
            i += 3
        }
        when (bytes.size - i) {
            1 -> {
                val bits = (bytes[i].toInt() and 0xff) shl 16
                out.append(Alphabet[bits shr 18 and 0x3f])
                out.append(Alphabet[bits shr 12 and 0x3f])
                out.append(Pad).append(Pad)
            }
            2 -> {
                val bits = ((bytes[i].toInt() and 0xff) shl 16) or
                    ((bytes[i + 1].toInt() and 0xff) shl 8)
                appendTriple(out, bits)
                out.append(Pad)
            }
        }
        return out.toString()
    }

    /** Null when [text] is not valid Base64. */
    fun decodeBase64(text: String): ByteArray? {
        val clean = text.filter { !it.isWhitespace() }
        if (clean.isEmpty()) return ByteArray(0)
        if (clean.length % 4 != 0 || clean.dropLast(2).contains(Pad)) return null
        val out = ByteArray(clean.length / 4 * 3)
        var written = 0
        var i = 0
        while (i < clean.length) {
            val c0 = Alphabet.indexOf(clean[i])
            val c1 = Alphabet.indexOf(clean[i + 1])
            val pad2 = clean[i + 2] == Pad
            val pad3 = clean[i + 3] == Pad
            val c2 = if (pad2) -1 else Alphabet.indexOf(clean[i + 2])
            val c3 = if (pad3) -1 else Alphabet.indexOf(clean[i + 3])
            if (c0 < 0 || c1 < 0) return null
            if ((!pad2 && c2 < 0) || (!pad3 && c3 < 0)) return null
            if (pad3 && !pad2) return null
            val bits = (c0 shl 18) or (c1 shl 12) or
                (if (c2 >= 0) c2 shl 6 else 0) or
                (if (c3 >= 0) c3 else 0)
            out[written++] = (bits shr 16 and 0xff).toByte()
            if (c2 >= 0) out[written++] = (bits shr 8 and 0xff).toByte()
            if (c3 >= 0) out[written++] = (bits and 0xff).toByte()
            i += 4
        }
        return out.copyOf(written)
    }

    private fun appendTriple(out: StringBuilder, bits: Int) {
        out.append(Alphabet[bits shr 18 and 0x3f])
        out.append(Alphabet[bits shr 12 and 0x3f])
        out.append(Alphabet[bits shr 6 and 0x3f])
    }
}
