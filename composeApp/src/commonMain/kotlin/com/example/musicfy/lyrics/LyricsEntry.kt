// LyricsEntry.kt

package com.example.musicfy.lyrics

import kotlinx.coroutines.flow.MutableStateFlow

data class WordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
)

/** One reading positioned over the characters it belongs to. */
data class RubyToken(
    val start: Int,
    val end: Int,
    val text: String,
)

data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
    val romanizedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    val rubyFlow: MutableStateFlow<List<RubyToken>?> = MutableStateFlow(null),
    val translatedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
    val agent: String? = null,
    val isBackground: Boolean = false,
    /** Timed pieces as sent when syllables were merged into whole [words]. */
    val syllables: List<WordTimestamp>? = null,
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = time.compareTo(other.time)

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}
