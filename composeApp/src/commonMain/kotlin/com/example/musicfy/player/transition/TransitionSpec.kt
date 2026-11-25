package com.example.musicfy.player.transition

/**
 * All of a transition that can be edited, stored or sent to a player adapter.
 * Pure Kotlin: no Android, no player engine, no persistence framework.
 */
data class TransitionSpec(
    val kind: TransitionKind = TransitionKind.CROSSFADE,
    val durationMs: Int = DefaultDurationMs,
    val curve: TransitionCurve = TransitionCurve.SMOOTH,
    val mixAmount: Float = 1f,
    val autoCrossfade: Boolean = true,
) {
    /** Duration clamped to the range used by upstream MixIt presets. */
    private val clampedDurationMs: Int
        get() = durationMs.coerceIn(MinDurationMs, MaxDurationMs)

    /** A hard cut has no overlap, so no time is spent in a transition. */
    val effectiveDurationMs: Int
        get() = if (kind.usesOverlap) clampedDurationMs else 0

    val effectiveMixAmount: Float
        get() = if (kind.usesOverlap) mixAmount.coerceIn(0f, 1f) else 0f

    /** Copy with every numeric field clamped, safe to persist or hand onward. */
    fun validated(): TransitionSpec = copy(
        durationMs = clampedDurationMs,
        mixAmount = effectiveMixAmount,
    )

    companion object {
        const val MinDurationMs: Int = 500
        const val MaxDurationMs: Int = 28_000
        const val DefaultDurationMs: Int = 8_000

        val Default: TransitionSpec = TransitionSpec()

        /** Defaults close to the upstream MixIt presets and CrossMix modes. */
        fun forKind(kind: TransitionKind): TransitionSpec = when (kind) {
            TransitionKind.NONE -> TransitionSpec(
                kind = kind,
                durationMs = MinDurationMs,
                curve = TransitionCurve.LINEAR,
                mixAmount = 0f,
                autoCrossfade = false,
            )
            TransitionKind.CROSSFADE -> TransitionSpec(
                kind = kind,
                durationMs = DefaultDurationMs,
                curve = TransitionCurve.SMOOTH,
                mixAmount = 1f,
                autoCrossfade = true,
            )
            TransitionKind.BLEND -> TransitionSpec(
                kind = kind,
                durationMs = 10_000,
                curve = TransitionCurve.EQUAL_POWER,
                mixAmount = 1f,
                autoCrossfade = true,
            )
            TransitionKind.SLIDE -> TransitionSpec(
                kind = kind,
                durationMs = 6_000,
                curve = TransitionCurve.SMOOTH,
                mixAmount = 0.9f,
                autoCrossfade = true,
            )
            TransitionKind.CAROUSEL -> TransitionSpec(
                kind = kind,
                durationMs = 7_000,
                curve = TransitionCurve.EQUAL_POWER,
                mixAmount = 0.9f,
                autoCrossfade = true,
            )
        }
    }
}
