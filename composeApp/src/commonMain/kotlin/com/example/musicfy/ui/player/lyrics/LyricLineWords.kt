// LyricLineWords.kt

package com.example.musicfy.ui.player.lyrics

import com.example.musicfy.lyrics.WordTimestamp

/**
 * The timed words of one lyric line, resolved against the characters of the
 * text that is actually drawn (the lead line, without bracketed backing
 * vocals). Kept separate from the parsed lyrics entry so the timing lookup
 * stays pure.
 */
class LyricLineWords(
    val words: List<SungWord>,
) {
    /** The last word that has started and has not finished at [positionMs]. */
    fun activeAt(positionMs: Long): SungWordProgress? = activeSungWord(this, positionMs)
}

/** Builds the word timing model from parsed timestamps, or an empty model when there are none. */
fun lyricLineWords(text: String, words: List<WordTimestamp>?): LyricLineWords {
    if (words.isNullOrEmpty()) return LyricLineWords(emptyList())

    val timed = ArrayList<SungWord>(words.size)
    var cursor = 0
    for (word in words) {
        val startMs = (word.startTime * 1_000.0).toLong()
        val endMs = (word.endTime * 1_000.0).toLong()
        val timing = WordTiming(startMs, endMs)
        if (!timing.isValid) continue

        val trimmed = word.text.trim()
        val from = cursor.coerceAtMost(text.length)
        val at = if (trimmed.isEmpty()) -1 else text.indexOf(trimmed, from)
        val range = if (at >= 0) {
            val found = at until (at + trimmed.length)
            cursor = found.last + 1
            found
        } else {
            IntRange.EMPTY
        }
        timed += SungWord(word.text, range, timing)
    }
    return LyricLineWords(timed)
}

/**
 * Pure playback-position lookup: the index and progress of the word being sung
 * at [positionMs], or null before the first word, in a gap, or after the line.
 * A line without word timings returns null, so callers keep their line-level
 * karaoke and glow unchanged.
 */
fun activeSungWord(line: LyricLineWords?, positionMs: Long): SungWordProgress? {
    val words = line?.words ?: return null
    if (words.isEmpty()) return null

    var foundIndex = -1
    for (index in words.indices) {
        if (positionMs < words[index].timing.startMs) break
        foundIndex = index
    }
    if (foundIndex < 0) return null

    val word = words[foundIndex]
    if (positionMs > word.timing.endMs) return null
    val elapsedMs = positionMs - word.timing.startMs
    return SungWordProgress(
        index = foundIndex,
        word = word,
        progress = word.timing.progressAt(positionMs),
        elapsedMs = elapsedMs,
    )
}
