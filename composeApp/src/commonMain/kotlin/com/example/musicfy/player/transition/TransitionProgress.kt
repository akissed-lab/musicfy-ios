package com.example.musicfy.player.transition

/**
 * One instant of a transition. Volumes are 0..1 linear gains and the pan
 * values are -1..1 offsets used only by the visual preview; nothing here
 * touches a real player.
 */
data class TransitionProgress(
    val elapsedMs: Long,
    val durationMs: Int,
    val fraction: Float,
    val easedProgress: Float,
    val outgoingVolume: Float,
    val incomingVolume: Float,
    val outgoingPan: Float,
    val incomingPan: Float,
) {
    val finished: Boolean
        get() = fraction >= 1f

    val isActive: Boolean
        get() = durationMs > 0 && fraction > 0f && fraction < 1f

    companion object {
        /** Position from elapsed time. A hard cut is already over at t = 0. */
        fun compute(spec: TransitionSpec, elapsedMs: Long): TransitionProgress {
            val duration = spec.effectiveDurationMs
            if (duration <= 0 || spec.kind == TransitionKind.NONE) {
                return TransitionProgress(
                    elapsedMs = elapsedMs.coerceAtLeast(0L),
                    durationMs = 0,
                    fraction = 1f,
                    easedProgress = 1f,
                    outgoingVolume = 0f,
                    incomingVolume = 1f,
                    outgoingPan = -1f,
                    incomingPan = 0f,
                )
            }

            val t = (elapsedMs.coerceIn(0L, duration.toLong()).toFloat() / duration.toFloat())
                .coerceIn(0f, 1f)
            val eased = spec.curve.ease(t)
            val (rawOut, rawIn) = spec.curve.gains(t)
            val amount = spec.effectiveMixAmount

            // amount = 0 is a hard cut; amount = 1 is the full curve. The very
            // last frame is explicit so gain never ends on a partial value.
            val outgoing = if (t >= 1f) 0f else 1f - (1f - rawOut) * amount
            val incoming = if (t >= 1f) 1f else rawIn * amount

            val (outPan, inPan) = when (spec.kind) {
                TransitionKind.CROSSFADE, TransitionKind.BLEND -> 0f to 0f
                TransitionKind.SLIDE -> -eased to (1f - eased)
                TransitionKind.CAROUSEL -> (-eased * 0.75f) to ((1f - eased) * 0.75f)
                TransitionKind.NONE -> -1f to 0f
            }

            return TransitionProgress(
                elapsedMs = elapsedMs.coerceIn(0L, duration.toLong()),
                durationMs = duration,
                fraction = t,
                easedProgress = eased,
                outgoingVolume = outgoing.coerceIn(0f, 1f),
                incomingVolume = incoming.coerceIn(0f, 1f),
                outgoingPan = outPan.coerceIn(-1f, 1f),
                incomingPan = inPan.coerceIn(-1f, 1f),
            )
        }

        /** Position from a normalized 0..1 playhead instead of a wall-clock time. */
        fun compute(spec: TransitionSpec, fraction: Float): TransitionProgress {
            val duration = spec.effectiveDurationMs
            val position = fraction.coerceIn(0f, 1f)
            return if (duration <= 0) {
                compute(spec, 0L)
            } else {
                compute(spec, (position * duration.toFloat()).toLong())
            }
        }
    }
}

/** Convenience for callers that already hold a spec. */
fun TransitionSpec.progressAt(elapsedMs: Long): TransitionProgress =
    TransitionProgress.compute(this, elapsedMs)

/** Convenience for UI sliders that work in 0..1 instead of milliseconds. */
fun TransitionSpec.progressAt(fraction: Float): TransitionProgress =
    TransitionProgress.compute(this, fraction)
