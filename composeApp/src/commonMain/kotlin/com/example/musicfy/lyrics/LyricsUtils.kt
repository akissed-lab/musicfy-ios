// LyricsUtils.kt
//
// KMP port of the provider-grading and parsing core of upstream LyricsUtils.
// The romanisation tables (kuromoji/Pinyin/Cyrillic) are a separate later
// stage; provider selection only needs parsing, script/language detection and
// sync quality, which are kept here with the same names and semantics.

package com.example.musicfy.lyrics

object LyricsUtils {
    val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
    val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

    private val RICH_SYNC_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.+)".toRegex()
    private val RICH_SYNC_WORD_REGEX = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>\\s*([^<]+)".toRegex()
    private val RICH_SYNC_TAG_REGEX = "<\\d{1,2}:\\d{2}\\.\\d{2,3}>".toRegex()

    private val AGENT_REGEX = "\\{agent:([^}]+)\\}".toRegex()
    private val BACKGROUND_REGEX = "^\\{bg\\}".toRegex()

    private val HEX_ENTITY_REGEX = "&#x([0-9a-fA-F]+);".toRegex()
    private val DEC_ENTITY_REGEX = "&#(\\d+);".toRegex()

    private fun decodeHtmlEntities(text: String): String =
        text
            .replace(HEX_ENTITY_REGEX) { match ->
                match.groupValues[1].toIntOrNull(16)
                    ?.takeIf { it in 0..0x10FFFF }
                    ?.let(::codePointToString)
                    ?: match.value
            }
            .replace(DEC_ENTITY_REGEX) { match ->
                match.groupValues[1].toIntOrNull()
                    ?.takeIf { it in 0..0x10FFFF }
                    ?.let(::codePointToString)
                    ?: match.value
            }
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")

    private fun codePointToString(cp: Int): String = when {
        cp <= 0xFFFF -> cp.toChar().toString()
        else -> {
            val v = cp - 0x10000
            charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
        }
    }

    fun isSyllableSplit(rawLyrics: String): Boolean {
        if (rawLyrics.isBlank()) return false
        val entries = runCatching { parseLyrics(rawLyrics) }.getOrNull() ?: return false
        if (entries.size < 4) return false

        var checked = 0
        var flagged = 0
        for (entry in entries) {
            val text = entry.text.trim()
            val letters = text.filter(Char::isLetter)
            if (letters.isEmpty()) continue

            val latin = letters.count { it.code < 0x250 }
            if (latin.toDouble() / letters.length < 0.9) continue

            val tokens = text.split(Regex("\\s+")).filter { it.any(Char::isLetter) }
            if (tokens.size < 4) continue
            checked++

            val lengths = tokens.map { it.count(Char::isLetter) }
            val shortRatio = lengths.count { it <= 3 }.toDouble() / lengths.size
            val mean = lengths.sum().toDouble() / lengths.size
            if (shortRatio >= 0.8 && mean < 2.8) flagged++
        }

        return checked >= 4 && flagged.toDouble() / checked >= 0.65
    }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        val unescaped = lyrics
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

        val lines = decodeHtmlEntities(unescaped).lines()
            .filter { it.isNotBlank() && !it.trim().startsWith("[offset:") }

        val isRichSync = lines.any { line ->
            RICH_SYNC_LINE_REGEX.matches(line.trim()) && RICH_SYNC_WORD_REGEX.containsMatchIn(line)
        }
        val parsed = if (isRichSync) parseRichSyncLyrics(lines) else parseStandardLyrics(lines)
        return fixWordSpacing(parsed)
    }

    fun parsePlainLyrics(lyrics: String): List<LyricsEntry> =
        decodeHtmlEntities(lyrics.trim().replace("\\n", "\n").replace("\\r", "\r"))
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !LRC_META_LINE_REGEX.matches(it) }
            .map { it.replace(LRC_TIME_TAG_REGEX, "").replace(RICH_SYNC_TAG_REGEX, "").trim() }
            .filter { it.isNotEmpty() }
            .map { LyricsEntry(0L, it) }

    private val LRC_TIME_TAG_REGEX = "\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?\\]".toRegex()
    private val LRC_META_LINE_REGEX = "^\\[[a-zA-Z#]+:.*\\]$".toRegex()

    private fun parseStandardLyrics(lines: List<String>): List<LyricsEntry> {
        val result = ArrayList<LyricsEntry>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.trim().startsWith("<") || !line.trim().endsWith(">")) {
                val entries = parseLine(line, null)
                if (entries != null) {
                    val wordTimestamps = if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1]
                        if (nextLine.trim().startsWith("<") && nextLine.trim().endsWith(">")) {
                            parseWordTimestamps(nextLine.trim().removeSurrounding("<", ">"))
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                    if (wordTimestamps != null) {
                        result.addAll(entries.map { entry ->
                            LyricsEntry(
                                entry.time,
                                entry.text,
                                wordTimestamps,
                                agent = entry.agent,
                                isBackground = entry.isBackground,
                            )
                        })
                    } else {
                        result.addAll(entries)
                    }
                }
            }
            i++
        }
        return result.sorted()
    }

    private fun parseWordTimestamps(data: String): List<WordTimestamp>? {
        if (data.isBlank()) return null
        return runCatching {
            data.split("|").mapNotNull { wordData ->
                val parts = wordData.split(":")
                if (parts.size == 3) {
                    WordTimestamp(
                        text = parts[0],
                        startTime = parts[1].toDouble(),
                        endTime = parts[2].toDouble(),
                    )
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun parseLine(line: String, words: List<WordTimestamp>? = null): List<LyricsEntry>? {
        if (line.isEmpty()) return null
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        var text = matchResult.groupValues[3]

        val agentMatch = AGENT_REGEX.find(text)
        val agent = agentMatch?.groupValues?.get(1)
        if (agentMatch != null) text = text.replaceFirst(AGENT_REGEX, "")

        val isBackground = BACKGROUND_REGEX.containsMatchIn(text)
        if (isBackground) text = text.replaceFirst(BACKGROUND_REGEX, "")

        return TIME_REGEX.findAll(times).map { timeMatch ->
            val min = timeMatch.groupValues[1].toLong()
            val sec = timeMatch.groupValues[2].toLong()
            val milString = timeMatch.groupValues[3]
            var mil = milString.toLong()
            if (milString.length == 2) mil *= 10
            LyricsEntry(min * 60_000L + sec * 1_000L + mil, text, words, agent = agent, isBackground = isBackground)
        }.toList()
    }

    // ---- rich sync ----------------------------------------------------------------------------

    private fun parseRichSyncLyrics(lines: List<String>): List<LyricsEntry> {
        val result = ArrayList<LyricsEntry>()
        lines.forEachIndexed { index, line ->
            val match = RICH_SYNC_LINE_REGEX.matchEntire(line.trim()) ?: return@forEachIndexed
            val lineTimeMs = richLineTimeMs(match)
            var content = match.groupValues[4].trimStart()

            val agentMatch = AGENT_REGEX.find(content)
            val agent = agentMatch?.groupValues?.get(1)
            if (agentMatch != null) content = content.replaceFirst(AGENT_REGEX, "")
            val isBackground = BACKGROUND_REGEX.containsMatchIn(content)
            if (isBackground) content = content.replaceFirst(BACKGROUND_REGEX, "")

            val words = parseRichSyncWords(content, index, lines)
            val plainText = repairRichSyncPlainText(content.replace(RICH_SYNC_TAG_REGEX, "").trim())
            if (plainText.isNotBlank()) {
                result += LyricsEntry(lineTimeMs, plainText, words, agent = agent, isBackground = isBackground)
            }
        }
        return result.sorted()
    }

    private fun richLineTimeMs(match: MatchResult): Long {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fraction = match.groupValues[3].toLongOrNull() ?: 0L
        val millis = if (match.groupValues[3].length == 3) fraction else fraction * 10
        return minutes * 60_000L + seconds * 1_000L + millis
    }

    private fun richSeconds(match: MatchResult): Double {
        val minutes = match.groupValues[1].toLongOrNull() ?: 0L
        val seconds = match.groupValues[2].toLongOrNull() ?: 0L
        val fraction = match.groupValues[3].toLongOrNull() ?: 0L
        val part = if (match.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
        return minutes * 60.0 + seconds + part
    }

    private fun getNextLineStartTime(currentIndex: Int, allLines: List<String>): Double? {
        if (currentIndex + 1 >= allLines.size) return null
        val match = RICH_SYNC_LINE_REGEX.matchEntire(allLines[currentIndex + 1].trim()) ?: return null
        return richSeconds(match)
    }

    private fun parseRichSyncWords(
        content: String,
        currentIndex: Int,
        allLines: List<String>,
    ): List<WordTimestamp>? {
        val matches = RICH_SYNC_WORD_REGEX.findAll(content).toList()
        if (matches.isEmpty()) return null
        val words = ArrayList<WordTimestamp>()
        matches.forEachIndexed { index, match ->
            val start = richSeconds(match)
            val end = if (index < matches.size - 1) {
                richSeconds(matches[index + 1])
            } else {
                getNextLineStartTime(currentIndex, allLines) ?: (start + 0.5)
            }
            val text = match.groupValues[4].trim()
            if (text.isNotBlank()) words += WordTimestamp(text, start, end)
        }
        return words.takeIf { it.isNotEmpty() }
    }

    /** Undoes the old syllable-split cache shape without gluing ordinary double-spaced text. */
    internal fun repairRichSyncPlainText(text: String): String {
        val compacted = text.replace(Regex("[\\t\\u00A0]+"), " ").trim()
        if (compacted.isBlank()) return compacted
        if (!Regex(" {2,}").containsMatchIn(compacted)) return compacted.replace(Regex(" +"), " ")

        val groups = compacted.split(Regex(" {2,}")).filter { it.isNotBlank() }
        val multiPiece = groups.count { it.trim().contains(' ') }
        if (multiPiece * 2 <= groups.size) return compacted.replace(Regex(" +"), " ")

        return groups.joinToString(" ") { wordGroup ->
            val parts = wordGroup.trim().split(Regex(" +")).filter { it.isNotBlank() }
            if (parts.size > 1 && parts.all { it.length <= 4 && it.any(Char::isLetter) }) {
                parts.joinToString("")
            } else {
                wordGroup.trim().replace(Regex(" +"), " ")
            }
        }
    }

    // ---- word timing repair -------------------------------------------------------------------

    private fun fixWordSpacing(entries: List<LyricsEntry>): List<LyricsEntry> {
        if (entries.none { (it.words?.size ?: 0) > 1 }) return entries
        val spacesDropped = spacesDropped(entries)
        return entries.map { entry ->
            val words = entry.words?.takeIf { it.size > 1 } ?: return@map entry
            val text = if (spacesDropped && isGluedLine(entry.text, words)) spacedText(words) else entry.text
            val merged = mergeWordPieces(text, words)
            if (text == entry.text && merged == words) {
                entry
            } else {
                entry.copy(text = text, words = merged, syllables = words.takeIf { it.size != merged.size })
            }
        }
    }

    private val UnspacedScripts = setOf(Script.HAN, Script.KANA, Script.THAI)

    private fun isGluedLine(text: String, words: List<WordTimestamp>): Boolean =
        words.count { it.text.isNotBlank() } >= 2 &&
            text.none(Char::isWhitespace) &&
            dominantScript(text).let { it !in UnspacedScripts && it != Script.UNKNOWN }

    private fun spacesDropped(entries: List<LyricsEntry>): Boolean {
        val lines = entries.filter { entry ->
            val words = entry.words ?: return@filter false
            words.count { it.text.isNotBlank() } >= 2 &&
                dominantScript(entry.text).let { it !in UnspacedScripts && it != Script.UNKNOWN }
        }
        if (lines.size < 3) return false
        val glued = lines.filter { it.text.none(Char::isWhitespace) }
        if (glued.size * 5 < lines.size * 4) return false
        val pieces = glued.sumOf { line -> line.words!!.count { it.text.isNotBlank() } }
        return pieces >= glued.size * 3
    }

    private const val NoSpaceBefore = ",.!?;:)]}:"
    private const val NoSpaceAfter = "([{"
    private val ContractionPiece = Regex("^['’](s|t|re|ll|m|ve|d)$", RegexOption.IGNORE_CASE)

    private fun spacedText(words: List<WordTimestamp>): String = buildString {
        words.map { it.text.trim() }.filter { it.isNotEmpty() }.forEach { piece ->
            val attached = piece.first() in NoSpaceBefore || ContractionPiece.matches(piece)
            if (isNotEmpty() && !attached && last() !in NoSpaceAfter) append(' ')
            append(piece)
        }
    }

    private fun mergeWordPieces(text: String, words: List<WordTimestamp>): List<WordTimestamp> {
        val pieces = words.filter { it.text.isNotBlank() }
        val ranges = wordCharRanges(text, pieces)
        val merged = ArrayList<WordTimestamp>(pieces.size)
        var lastEnd = -1
        pieces.forEachIndexed { index, piece ->
            val range = ranges[index]
            val previous = merged.lastOrNull()
            val glued = !range.isEmpty() && range.first == lastEnd &&
                lastEnd > 0 &&
                scriptOf(text[lastEnd - 1]) !in UnspacedScripts &&
                scriptOf(text[range.first]) !in UnspacedScripts
            if (previous != null && glued) {
                merged[merged.lastIndex] = WordTimestamp(
                    previous.text + piece.text.trim(),
                    previous.startTime,
                    maxOf(previous.endTime, piece.endTime),
                )
            } else {
                merged += WordTimestamp(piece.text.trim(), piece.startTime, piece.endTime)
            }
            lastEnd = if (range.isEmpty()) -1 else range.last + 1
        }
        return merged
    }

    fun wordCharRanges(text: String, words: List<WordTimestamp>): List<IntRange> {
        var searchFrom = 0
        return words.map { word ->
            val trimmed = word.text.trim()
            if (trimmed.isEmpty()) return@map IntRange.EMPTY
            val start = text.indexOf(trimmed, searchFrom)
            if (start < 0) return@map IntRange.EMPTY
            searchFrom = start + trimmed.length
            start until searchFrom
        }
    }

    // ---- playback helpers ---------------------------------------------------------------------

    const val LineLeadInMs = 300L
    private const val UntimedLineDurationMs = 5_000L

    fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int {
        if (lines.isEmpty()) return -1
        if (position >= lyricsEndMs(lines)) return lines.size
        for (index in lines.indices) {
            if (lines[index].time >= position + LineLeadInMs) return index - 1
        }
        return lines.lastIndex
    }

    fun lineEndMs(lines: List<LyricsEntry>, index: Int): Long {
        val entry = lines.getOrNull(index) ?: return 0L
        entry.words?.lastOrNull()?.let { return (it.endTime * 1000).toLong() }
        lines.getOrNull(index + 1)?.let { return it.time }
        return entry.time + UntimedLineDurationMs
    }

    fun lyricsEndMs(lines: List<LyricsEntry>): Long {
        if (lines.isEmpty()) return 0L
        return lineEndMs(lines, lines.lastIndex)
    }

    // ---- grading ------------------------------------------------------------------------------

    fun isUnsynced(lines: List<LyricsEntry>): Boolean {
        if (lines.size < 2) return false
        if (lines.any { !it.words.isNullOrEmpty() }) return false
        val first = lines.first().time
        return lines.all { it.time == first }
    }

    enum class SyncKind { NONE, PLAIN, LINE, WORD }

    fun syncKind(rawLyrics: String): SyncKind {
        if (rawLyrics.isBlank() || rawLyrics == "LYRICS_NOT_FOUND") return SyncKind.NONE
        val entries = runCatching { parseLyrics(rawLyrics) }.getOrNull() ?: return SyncKind.NONE
        if (entries.isEmpty()) {
            return if (rawLyrics.any(Char::isLetter)) SyncKind.PLAIN else SyncKind.NONE
        }
        if (isUnsynced(entries)) return SyncKind.PLAIN
        val wordSynced = entries.count { ((it.syllables ?: it.words)?.size ?: 0) > 1 }
        if (wordSynced * 2 >= entries.size && wordSynced > 0) return SyncKind.WORD
        return SyncKind.LINE
    }

    enum class Script { LATIN, HAN, KANA, HANGUL, CYRILLIC, ARABIC, DEVANAGARI, THAI, GREEK, HEBREW, UNKNOWN }

    private fun scriptOf(ch: Char): Script? {
        if (!ch.isLetter()) return null
        return when (ch.code) {
            in 0x0041..0x024F -> Script.LATIN
            in 0x0370..0x03FF -> Script.GREEK
            in 0x0400..0x052F -> Script.CYRILLIC
            in 0x0590..0x05FF -> Script.HEBREW
            in 0x0600..0x06FF, in 0x0750..0x077F -> Script.ARABIC
            in 0x0900..0x097F -> Script.DEVANAGARI
            in 0x0E00..0x0E7F -> Script.THAI
            in 0x3040..0x30FF -> Script.KANA
            in 0xAC00..0xD7AF, in 0x1100..0x11FF -> Script.HANGUL
            in 0x3400..0x4DBF, in 0x4E00..0x9FFF, in 0xF900..0xFAFF -> Script.HAN
            else -> null
        }
    }

    fun dominantScript(text: String): Script {
        val counts = HashMap<Script, Int>()
        var total = 0
        for (ch in text) {
            val script = scriptOf(ch) ?: continue
            counts[script] = (counts[script] ?: 0) + 1
            total++
        }
        if (total == 0) return Script.UNKNOWN
        if ((counts[Script.KANA] ?: 0) > 0) return Script.KANA
        if ((counts[Script.HANGUL] ?: 0) * 4 >= total) return Script.HANGUL
        return counts.maxByOrNull { it.value }?.key ?: Script.UNKNOWN
    }

    fun lyricsScript(rawLyrics: String): Script {
        val stripped = rawLyrics
            .replace(Regex("\\[[^\\]]*\\]"), " ")
            .replace(Regex("<[^>]*>"), " ")
        return dominantScript(stripped)
    }

    fun hasVoiceTags(rawLyrics: String): Boolean = rawLyrics.contains("{agent:")

    fun hasDistinctVoices(rawLyrics: String): Boolean {
        if (!hasVoiceTags(rawLyrics)) return false
        val lines = runCatching { parseLyrics(rawLyrics) }.getOrNull() ?: return false
        return lines.filter { !it.isBackground }.map { it.agent ?: "v1" }.distinct().size > 1
    }

    private val MarkupRegex = Regex("""\[[^\]]*\]|<[^>]*>|\{[^}]*\}""")
    private val NonWordRegex = Regex("""[^\p{L}']+""")

    private val LanguageMarkers: Map<String, Set<String>> = mapOf(
        "en" to setOf(
            "the", "and", "you", "i", "i'm", "my", "it's", "don't", "your", "that", "what", "with",
            "this", "be", "just", "can't", "won't", "when", "all", "for", "of", "we", "but", "now",
            "never", "you're", "i'll", "was", "are", "can", "know", "like", "want", "she", "he",
        ),
        "id" to setOf(
            "yang", "dan", "aku", "kau", "kamu", "ini", "itu", "tak", "tidak", "dengan", "untuk",
            "akan", "ada", "dalam", "hati", "saja", "bisa", "tapi", "jangan", "sudah", "lagi",
            "karena", "selalu", "engkau", "dia", "kita", "cinta", "ku", "mu", "dari", "pun",
            "pernah", "hanya", "semua", "takkan", "denganmu", "padamu", "diriku", "dirimu",
        ),
        "es" to setOf(
            "que", "y", "el", "los", "las", "yo", "pero", "porque", "quiero", "eres", "estoy",
            "cuando", "para", "del", "como", "mas", "nada", "todo", "tu", "mi", "si", "esta",
            "contigo", "corazon", "vida", "siempre", "nunca", "puedo",
        ),
        "pt" to setOf(
            "voce", "nao", "eu", "meu", "minha", "e", "um", "pra", "tudo", "tambem", "mais",
            "quando", "sem", "isso", "ela", "ele", "so", "vou", "seu", "sua", "nos", "estou",
            "coracao", "agora", "sempre", "nunca",
        ),
        "fr" to setOf(
            "je", "les", "et", "est", "pas", "pour", "dans", "moi", "toi", "mon", "c'est", "j'ai",
            "qui", "sur", "une", "des", "du", "au", "ne", "plus", "tout", "elle", "nous", "vous",
            "suis", "ca", "avec", "mais",
        ),
        "de" to setOf(
            "der", "die", "das", "und", "ich", "nicht", "ist", "ein", "eine", "mich", "mir", "dich",
            "dir", "wir", "mein", "auf", "mit", "wie", "nur", "auch", "sich", "noch", "bin", "bist",
            "kein", "immer", "zu",
        ),
        "it" to setOf(
            "il", "che", "non", "sono", "sei", "io", "per", "mio", "mia", "piu", "cosa", "ancora",
            "questo", "della", "nel", "perche", "tutto", "anche", "gli", "voglio", "amore",
        ),
        "tl" to setOf(
            "ang", "ng", "sa", "ko", "mo", "na", "ka", "ako", "ikaw", "hindi", "lang", "mga",
            "kita", "ay", "naman", "pa", "sana", "akin", "ito", "iyong", "natin", "tayo", "puso",
        ),
        "nl" to setOf(
            "het", "een", "ik", "niet", "van", "dat", "mijn", "jij", "wij", "voor", "maar", "zijn",
            "wat", "wel", "nog", "ook", "geen", "heb", "ben",
        ),
        "tr" to setOf(
            "bir", "ve", "ben", "sen", "bu", "icin", "cok", "gibi", "beni", "seni", "var", "yok",
            "degil", "olan", "her", "ama", "daha", "sey", "kadar",
        ),
        "vi" to setOf(
            "anh", "em", "va", "khong", "la", "c?a", "m?t", "ngu?i", "yeu", "co", "nh?ng", "cho",
            "da", "minh", "trong", "nhu", "toi", "thi", "nay",
        ),
        "ja-Latn" to setOf(
            "wa", "ga", "wo", "ni", "mo", "kimi", "boku", "watashi", "anata", "dake", "kara",
            "made", "kokoro", "demo", "itsumo", "nai", "koto", "toki", "naka", "sora",
        ),
        "ko-Latn" to setOf(
            "naega", "neol", "neoreul", "nan", "nae", "neon", "geu", "nal", "naneun", "neoui",
            "uri", "saranghae", "jigeum", "modeun", "ije", "geurae", "eonjena",
        ),
    )

    fun latinLanguage(rawLyrics: String): String? {
        if (lyricsScript(rawLyrics) != Script.LATIN) return null
        val tokens = rawLyrics
            .replace(MarkupRegex, " ")
            .lowercase()
            .split(NonWordRegex)
            .map { it.trim('\'', '’') }
            .filter { it.isNotEmpty() }
        if (tokens.size < 12) return null
        val ranked = LanguageMarkers
            .map { (language, markers) -> language to tokens.count { it in markers } }
            .sortedByDescending { it.second }
        val (best, hits) = ranked.first()
        val runnerUp = ranked.getOrNull(1)?.second ?: 0
        return best.takeIf { hits * 10 >= tokens.size && hits * 2 >= runnerUp * 3 }
    }
}
