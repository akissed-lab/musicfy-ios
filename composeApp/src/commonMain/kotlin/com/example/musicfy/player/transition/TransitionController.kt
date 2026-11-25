package com.example.musicfy.player.transition

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Small Compose-friendly state holder for the transition creator.
 *
 * It only owns edit and preview state. Applying a transition is deliberately
 * left to the caller (`onApply` on the sheet), so this can be wired to any
 * future player adapter without touching Android or AVFoundation code.
 */
@Stable
class TransitionController(initialSpec: TransitionSpec = TransitionSpec()) {

    var spec: TransitionSpec by mutableStateOf(initialSpec.validated())
        private set

    /** Where the live preview playhead is, in milliseconds. */
    var previewElapsedMs: Long by mutableStateOf(0L)

    /** Driven by the sheet's frame loop; no real playback is started here. */
    var previewPlaying: Boolean by mutableStateOf(false)
        private set

    val progress: TransitionProgress
        get() = TransitionProgress.compute(spec, previewElapsedMs)

    fun update(next: TransitionSpec) {
        spec = next.validated()
        previewElapsedMs = previewElapsedMs.coerceAtMost(spec.effectiveDurationMs.toLong())
    }

    /** Picking a kind loads that kind's defaults, as upstream presets do. */
    fun setKind(kind: TransitionKind) = update(TransitionSpec.forKind(kind))

    fun setDurationMs(durationMs: Int) = update(spec.copy(durationMs = durationMs))

    fun setCurve(curve: TransitionCurve) = update(spec.copy(curve = curve))

    fun setMixAmount(amount: Float) = update(spec.copy(mixAmount = amount))

    fun setAutoCrossfade(enabled: Boolean) = update(spec.copy(autoCrossfade = enabled))

    fun setPreviewFraction(fraction: Float) {
        val duration = spec.effectiveDurationMs
        previewElapsedMs = if (duration <= 0) {
            0L
        } else {
            (fraction.coerceIn(0f, 1f) * duration.toFloat()).toLong()
        }
    }

    fun playPreview() {
        previewPlaying = spec.effectiveDurationMs > 0
        if (!previewPlaying) previewElapsedMs = 0L
    }

    fun pausePreview() {
        previewPlaying = false
    }

    fun restartPreview() {
        previewElapsedMs = 0L
        previewPlaying = spec.effectiveDurationMs > 0
    }

    fun reset() {
        update(TransitionSpec.Default)
        pausePreview()
        previewElapsedMs = 0L
    }
}
