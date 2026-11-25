// WordTiming.kt

package com.example.musicfy.ui.player.lyrics

/**
 * When one sung word starts and ends, in the same milliseconds as the lyrics
 * offset applied to the playback position.
 */
data class WordTiming(
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    /** 0 at the start of the word, 1 at its end; outside the word it is clamped. */
    fun progressAt(positionMs: Long): Float {
        val duration = durationMs
        if (duration <= 0L) return if (positionMs >= startMs) 1f else 0f
        return ((positionMs - startMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }

    val isValid: Boolean get() = durationMs > 0L
}
