// ModuleLrcParser.kt
//
// Pure Kotlin parser for the portable part of upstream's word-by-word lyrics:
// normal LRC lines plus enhanced <mm:ss.xx> word stamps. It only reads the text
// the host already has; it never opens a file, a URL or an archive.

package com.example.musicfy.modules

internal object ModuleLrcParser {
    private val LineStamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]""")
    private val WordStamp = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")

    private const val MaxChars = 64_000
    private const val MaxLines = 400
    private const val MaxLineChars = 400
    private const val MaxWords = 80
    private const val MaxWordChars = 120

    fun parse(raw: String?): ModuleLyrics? {
        if (raw.isNullOrBlank()) return null

        val parsed = mutableListOf<Triple<Long, String, List<ModuleLyricWord>>>()
        for (source in raw.take(MaxChars).lineSequence().take(MaxLines)) {
            val stamp = LineStamp.find(source) ?: continue
            val start = millis(stamp) ?: continue
            var body = source.substring(stamp.range.last + 1).trim()

            // Some services repeat a line stamp before the text; only the first one is the time.
            while (body.startsWith("[")) {
                val extra = LineStamp.find(body) ?: break
                if (extra.range.first != 0) break
                body = body.substring(extra.range.last + 1).trimStart()
            }
            if (body.isEmpty()) continue
            if (body.length > MaxLineChars) body = body.take(MaxLineChars)

            val words = parseWords(body)
            val text = if (words.isEmpty()) body.trim() else words.joinToString(" ") { it.text }.trim()
            if (text.isEmpty()) continue
            parsed += Triple(start, text, words)
        }
        if (parsed.isEmpty()) return null

        val sorted = parsed.sortedBy { it.first }
        val lines = sorted.mapIndexed { index, item ->
            val nextStart = sorted.getOrNull(index + 1)?.first
            val lastWordEnd = item.third.lastOrNull()?.endMs
            val end = listOfNotNull(nextStart, lastWordEnd).minOrNull()
            ModuleLyricLine(
                startMs = item.first,
                endMs = end,
                text = item.second,
                words = item.third,
            )
        }
        return ModuleLyrics(lines = lines, sourceLabel = "Enhanced LRC")
    }

    private fun parseWords(body: String): List<ModuleLyricWord> {
        val stamps = WordStamp.findAll(body).toList()
        if (stamps.isEmpty()) return emptyList()

        val prefix = body.take(stamps.first().range.first).trim()
        val result = mutableListOf<ModuleLyricWord>()
        stamps.forEachIndexed { index, stamp ->
            val start = millis(stamp) ?: return@forEachIndexed
            val from = stamp.range.last + 1
            val to = stamps.getOrNull(index + 1)?.range?.first ?: body.length
            val chunk = body.substring(from, to).trim()
            if (chunk.isEmpty()) return@forEachIndexed
            val text = if (index == 0 && prefix.isNotEmpty()) "$prefix $chunk" else chunk
            val end = stamps.getOrNull(index + 1)?.let(::millis)
            result += ModuleLyricWord(
                text = text.take(MaxWordChars),
                startMs = start,
                endMs = end,
            )
            if (result.size >= MaxWords) return result
        }
        return result
    }

    private fun millis(match: MatchResult): Long? =
        milliseconds(match.groupValues[1], match.groupValues[2], match.groupValues[3])

    private fun milliseconds(minutes: String, seconds: String, fraction: String): Long? {
        val min = minutes.toLongOrNull() ?: return null
        val sec = seconds.toLongOrNull() ?: return null
        if (sec > 59L) return null
        val ms = if (fraction.isEmpty()) 0 else fraction.take(3).padEnd(3, '0').toIntOrNull()?.coerceIn(0, 999) ?: 0
        return min * 60_000L + sec * 1_000L + ms
    }
}
