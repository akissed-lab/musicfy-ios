// EmbeddedLyrics.kt
//
// Port of the upstream tag reader: ID3 (USLT/SYLT/TXXX) in MP3, Vorbis
// comments in FLAC and the ©lyr atom in M4A. Only bytes are read; audio is
// never decoded. The platform just supplies random access to the file.

package com.example.musicfy.lyrics

/** Random access to a file's bytes. */
interface LyricsSource {
    val size: Long
    fun read(position: Long, length: Int): ByteArray
}

/** The lyrics in the file at [uri], as [EmbeddedLyrics.find] reads them; null when none/unreadable. */
expect fun readEmbeddedLyrics(uri: String): String?

object EmbeddedLyrics {
    /** What `LyricsEntity.provider` says for these. */
    const val Provider = "Embedded"

    fun find(source: LyricsSource): String? {
        val head = source.read(0, 12)
        return when {
            head.startsWith("ID3") -> id3(source) ?: id3TagEnd(source)?.let { end ->
                // a FLAC can carry an ID3 tag in front of its own
                if (source.read(end, 4).startsWith("fLaC")) flac(source, end) else null
            }
            head.startsWith("fLaC") -> flac(source, 0)
            head.size >= 8 && latin1(head, 4, 8) == "ftyp" -> mp4(source)
            else -> null
        }
    }

    // ---- ID3v2 (MP3) --------------------------------------------------------------------------

    private fun id3TagEnd(source: LyricsSource): Long? {
        val header = source.read(0, 10)
        if (header.size < 10) return null
        val footer = header[3].toInt() == 4 && header[5].toInt() and 0x10 != 0
        return 10L + syncsafe(header, 6) + if (footer) 10 else 0
    }

    private fun id3(source: LyricsSource): String? {
        val header = source.read(0, 10)
        if (header.size < 10) return null
        val major = header[3].toInt()
        if (major != 3 && major != 4) return null
        val flags = header[5].toInt()
        val tagSize = syncsafe(header, 6)
        if (tagSize <= 0) return null
        val tag: LyricsSource = if (major == 3 && flags and 0x80 != 0) {
            if (tagSize > MaxTagBytes) return null
            BytesSource(deUnsync(source.read(10, tagSize)))
        } else {
            SliceSource(source, 10, tagSize.toLong())
        }

        var pos = 0L
        if (flags and 0x40 != 0) {
            val extended = tag.read(0, 4)
            if (extended.size < 4) return null
            pos = if (major == 4) {
                syncsafe(extended, 0).toLong()
            } else {
                int32(extended, 0).toLong() + 4
            }
        }
        val found = ArrayList<String>()
        while (pos + 10 <= tag.size) {
            val frameHeader = tag.read(pos, 10)
            if (frameHeader.size < 10 || frameHeader[0].toInt() == 0) break
            val id = latin1(frameHeader, 0, 4)
            val frameSize = if (major == 4) syncsafe(frameHeader, 4) else int32(frameHeader, 4)
            val frameFlags = (frameHeader[8].toInt() and 0xFF shl 8) or (frameHeader[9].toInt() and 0xFF)
            val start = pos + 10
            if (frameSize <= 0 || start + frameSize > tag.size) break
            pos = start + frameSize

            val compressedOrEncrypted =
                if (major == 4) frameFlags and 0x000C != 0 else frameFlags and 0x00C0 != 0
            if (compressedOrEncrypted || id !in LyricFrames || frameSize > MaxTagBytes) continue
            var skip = 0
            if (major == 4 && frameFlags and 0x0040 != 0 || major == 3 && frameFlags and 0x0020 != 0) {
                skip += 1
            }
            if (major == 4 && frameFlags and 0x0001 != 0) skip += 4
            if (skip >= frameSize) continue
            var body = tag.read(start + skip, frameSize - skip)
            if (major == 4 && frameFlags and 0x0002 != 0) body = deUnsync(body)

            when (id) {
                "SYLT" -> sylt(body)
                "USLT" -> uslt(body)
                else -> txxx(body)
            }?.let(found::add)
        }
        return pick(found)
    }

    private class BytesSource(private val bytes: ByteArray) : LyricsSource {
        override val size: Long = bytes.size.toLong()

        override fun read(position: Long, length: Int): ByteArray {
            if (position < 0 || position >= size || length <= 0) return ByteArray(0)
            val end = minOf(size, position + length).toInt()
            return bytes.copyOfRange(position.toInt(), end)
        }
    }

    private class SliceSource(
        private val base: LyricsSource,
        private val offset: Long,
        length: Long,
    ) : LyricsSource {
        override val size: Long = minOf(length, (base.size - offset).coerceAtLeast(0))

        override fun read(position: Long, length: Int): ByteArray {
            if (position < 0 || position >= size || length <= 0) return ByteArray(0)
            return base.read(offset + position, minOf(length.toLong(), size - position).toInt())
        }
    }

    private fun uslt(body: ByteArray): String? {
        if (body.size < 5) return null
        val charset = charsetOf(body[0].toInt()) ?: return null
        val textStart = terminatorEnd(body, 4, body[0].toInt())
        return decode(body, textStart, body.size, charset)
    }

    private fun txxx(body: ByteArray): String? {
        if (body.size < 2) return null
        val encoding = body[0].toInt()
        val charset = charsetOf(encoding) ?: return null
        val descriptionEnd = terminatorEnd(body, 1, encoding)
        val description = decode(body, 1, descriptionEnd, charset)?.trim()?.uppercase() ?: return null
        if (!LyricKeys.contains(description)) return null
        return decode(body, descriptionEnd, body.size, charset)
    }

    /** Synced lyrics as LRC. Frame-timed SYLT is ignored: it needs the audio to map frames to time. */
    private fun sylt(body: ByteArray): String? {
        if (body.size < 7) return null
        val encoding = body[0].toInt()
        val charset = charsetOf(encoding) ?: return null
        if (body[4].toInt() != 2) return null
        var pos = terminatorEnd(body, 6, encoding)
        val entries = ArrayList<Pair<Long, String>>()
        while (pos < body.size) {
            val textEnd = terminatorEnd(body, pos, encoding)
            if (textEnd + 4 > body.size) break
            val text = decode(body, pos, textEnd, charset) ?: ""
            entries += int32(body, textEnd).toLong() to text
            pos = textEnd + 4
        }
        if (entries.isEmpty()) return null
        val breaksMarkLines = entries.any { (_, text) -> text.startsWith('\n') || text.startsWith('\r') }
        val lines = ArrayList<Pair<Long, StringBuilder>>()
        for ((time, text) in entries) {
            val startsLine = !breaksMarkLines || lines.isEmpty() ||
                text.startsWith('\n') || text.startsWith('\r')
            if (startsLine) {
                lines += time to StringBuilder(text.trim())
            } else {
                lines.last().second.append(text)
            }
        }
        return lines
            .filter { (_, text) -> text.isNotBlank() }
            .joinToString("\n") { (time, text) -> "${lrcTime(time)}${text.toString().trim()}" }
            .takeIf { it.isNotBlank() }
    }

    // ---- FLAC ---------------------------------------------------------------------------------

    private fun flac(source: LyricsSource, start: Long): String? {
        var pos = start + 4
        repeat(MaxFlacBlocks) {
            val header = source.read(pos, 4)
            if (header.size < 4) return null
            val last = header[0].toInt() and 0x80 != 0
            val type = header[0].toInt() and 0x7F
            val length = (header[1].toInt() and 0xFF shl 16) or
                (header[2].toInt() and 0xFF shl 8) or
                (header[3].toInt() and 0xFF)
            if (type == VorbisCommentBlock) {
                return if (length in 1..MaxTagBytes) {
                    vorbisComments(source.read(pos + 4, length))
                } else {
                    null
                }
            }
            if (last) return null
            pos += 4 + length
        }
        return null
    }

    private fun vorbisComments(block: ByteArray): String? {
        var pos = 0
        if (pos + 4 > block.size) return null
        pos += 4 + le32(block, pos)
        if (pos + 4 > block.size) return null
        val count = le32(block, pos)
        pos += 4
        val found = ArrayList<String>()
        for (i in 0 until count) {
            if (pos + 4 > block.size) break
            val length = le32(block, pos)
            pos += 4
            if (length < 0 || pos + length > block.size) break
            val comment = decode(block, pos, pos + length, TextEncoding.UTF8) ?: ""
            pos += length
            val eq = comment.indexOf('=')
            if (eq > 0 && LyricKeys.contains(comment.substring(0, eq).trim().uppercase())) {
                found += comment.substring(eq + 1)
            }
        }
        return pick(found)
    }

    // ---- MP4 / M4A ----------------------------------------------------------------------------

    private fun mp4(source: LyricsSource): String? {
        val moov = atom(source, 0, source.size, "moov") ?: return null
        val udta = atom(source, moov.first, moov.second, "udta") ?: return null
        val meta = atom(source, udta.first, udta.second, "meta") ?: return null
        val ilst = atom(source, meta.first + 4, meta.second, "ilst")
            ?: atom(source, meta.first, meta.second, "ilst")
            ?: return null
        val lyrics = atom(source, ilst.first, ilst.second, "©lyr") ?: return null
        val data = atom(source, lyrics.first, lyrics.second, "data") ?: return null
        val length = data.second - data.first - 8
        if (length <= 0 || length > MaxTagBytes) return null
        val textBytes = source.read(data.first + 8, length.toInt())
        val text = decode(textBytes, 0, textBytes.size, TextEncoding.UTF8) ?: ""
        return pick(listOf(text))
    }

    private fun atom(source: LyricsSource, from: Long, to: Long, type: String): Pair<Long, Long>? {
        var pos = from
        while (pos + 8 <= to) {
            val header = source.read(pos, 16)
            if (header.size < 8) return null
            var size = int32(header, 0).toLong() and 0xFFFFFFFFL
            var headerSize = 8
            when (size) {
                1L -> {
                    if (header.size < 16) return null
                    size = (int32(header, 8).toLong() and 0xFFFFFFFFL shl 32) or
                        (int32(header, 12).toLong() and 0xFFFFFFFFL)
                    headerSize = 16
                }
                0L -> size = to - pos
            }
            if (size < headerSize) return null
            if (latin1(header, 4, 8) == type) {
                return (pos + headerSize) to minOf(pos + size, to)
            }
            pos += size
        }
        return null
    }

    // ---- shared -------------------------------------------------------------------------------

    private fun pick(found: List<String>): String? {
        val texts = found
            .map { it.replace("\r\n", "\n").replace('\r', '\n').trim('\u0000', ' ', '\n') }
            .filter { it.isNotBlank() }
        return texts.firstOrNull { LrcTimestamp.containsMatchIn(it) } ?: texts.firstOrNull()
    }

    private fun lrcTime(ms: Long): String =
        "[${(ms / 60_000).toString().padStart(2, '0')}:" +
            "${(ms / 1000 % 60).toString().padStart(2, '0')}." +
            "${(ms % 1000 / 10).toString().padStart(2, '0')}]"

    /** KMP replacement for JVM `java.nio.charset.Charset`. */
    private enum class TextEncoding {
        LATIN1,
        UTF8,
        UTF16,
        UTF16BE,
        UTF16LE,
    }

    private fun charsetOf(encoding: Int): TextEncoding? = when (encoding) {
        0 -> TextEncoding.LATIN1
        1 -> TextEncoding.UTF16
        2 -> TextEncoding.UTF16BE
        3 -> TextEncoding.UTF8
        else -> null
    }

    private fun latin1(bytes: ByteArray, from: Int, to: Int): String = buildString {
        for (i in from until minOf(to, bytes.size)) append(bytes[i].toInt().toChar())
    }

    private fun decode(bytes: ByteArray, from: Int, to: Int, charset: TextEncoding): String? {
        if (from >= to || from < 0 || to > bytes.size) return null
        val text = when (charset) {
            TextEncoding.LATIN1 -> latin1(bytes, from, to)
            TextEncoding.UTF8 -> bytes.copyOfRange(from, to).decodeToString()
            TextEncoding.UTF16BE -> utf16(bytes, from, to, bigEndian = true)
            TextEncoding.UTF16LE -> utf16(bytes, from, to, bigEndian = false)
            TextEncoding.UTF16 -> when {
                to - from >= 2 && bytes[from].toInt() and 0xFF == 0xFE &&
                    bytes[from + 1].toInt() and 0xFF == 0xFF -> utf16(bytes, from + 2, to, true)
                to - from >= 2 && bytes[from].toInt() and 0xFF == 0xFF &&
                    bytes[from + 1].toInt() and 0xFF == 0xFE -> utf16(bytes, from + 2, to, false)
                else -> utf16(bytes, from, to, bigEndian = false)
            }
        }
        return text.trimEnd('\u0000')
    }

    private fun utf16(bytes: ByteArray, from: Int, to: Int, bigEndian: Boolean): String = buildString {
        var i = from
        while (i + 1 < to) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = bytes[i + 1].toInt() and 0xFF
            val code = if (bigEndian) (b0 shl 8) or b1 else (b1 shl 8) or b0
            append(code.toChar())
            i += 2
        }
    }

    private fun terminatorEnd(bytes: ByteArray, from: Int, encoding: Int): Int {
        if (encoding == 1 || encoding == 2) {
            var i = from
            while (i + 1 < bytes.size) {
                if (bytes[i].toInt() == 0 && bytes[i + 1].toInt() == 0) return i + 2
                i += 2
            }
        } else {
            for (i in from until bytes.size) if (bytes[i].toInt() == 0) return i + 1
        }
        return bytes.size
    }

    /** ID3's unsynchronisation: every 0xFF 0x00 was written for a 0xFF. */
    private fun deUnsync(bytes: ByteArray): ByteArray {
        val out = ArrayList<Byte>(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val byte = bytes[i]
            out += byte
            if (byte.toInt() and 0xFF == 0xFF && i + 1 < bytes.size && bytes[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    private fun syncsafe(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0x7F shl 21) or (b[at + 1].toInt() and 0x7F shl 14) or
            (b[at + 2].toInt() and 0x7F shl 7) or (b[at + 3].toInt() and 0x7F)

    private fun int32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF shl 24) or (b[at + 1].toInt() and 0xFF shl 16) or
            (b[at + 2].toInt() and 0xFF shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun le32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or (b[at + 1].toInt() and 0xFF shl 8) or
            (b[at + 2].toInt() and 0xFF shl 16) or (b[at + 3].toInt() and 0xFF shl 24)

    private fun ByteArray.startsWith(text: String): Boolean =
        size >= text.length && text.indices.all { this[it] == text[it].code.toByte() }

    private val LyricFrames = setOf("USLT", "SYLT", "TXXX")
    private val LyricKeys = setOf("LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS", "SYNCEDLYRICS")
    private val LrcTimestamp = Regex("""\[\d{1,2}:\d{2}([.:]\d{1,3})?\]""")
    private const val VorbisCommentBlock = 4
    private const val MaxFlacBlocks = 64
    private const val MaxTagBytes = 16 * 1024 * 1024
}
