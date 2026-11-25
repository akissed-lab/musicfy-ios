// SungWord.kt

package com.example.musicfy.ui.player.lyrics

/** One word of a lyric line, with the characters it occupies in the line's text. */
data class SungWord(
    val text: String,
    val range: IntRange,
    val timing: WordTiming,
)

/** The word being sung at a playback position, and how far through it the singer is. */
data class SungWordProgress(
    val index: Int,
    val word: SungWord,
    val progress: Float,
    val elapsedMs: Long,
)
