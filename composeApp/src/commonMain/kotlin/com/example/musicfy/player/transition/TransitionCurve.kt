package com.example.musicfy.player.transition

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Shape of the volume hand-over. Kept separate from [TransitionKind] so a
 * creator can pick a visual and a curve independently, the same way upstream
 * MixIt keeps its fade points separate from its presets.
 */
enum class TransitionCurve(
    val label: String,
    val description: String,
) {
    LINEAR(
        label = "Linear",
        description = "Constant fade, the simplest crossfade.",
    ),
    SMOOTH(
        label = "Smooth",
        description = "Eases in and out so neither edge clicks.",
    ),
    EQUAL_POWER(
        label = "Equal power",
        description = "Keeps the combined loudness steady in the middle.",
    ),
    EXPONENTIAL(
        label = "Exponential",
        description = "Stays longer on the outgoing song, then moves quickly.",
    );

    /** Linear position 0..1 mapped through this curve. */
    internal fun ease(position: Float): Float {
        val t = position.coerceIn(0f, 1f)
        return when (this) {
            LINEAR -> t
            SMOOTH -> t * t * (3f - 2f * t)
            EQUAL_POWER -> sin(t * HalfPi)
            EXPONENTIAL -> ((exp(ExponentialK * t) - 1f) / (exp(ExponentialK) - 1f)).coerceIn(0f, 1f)
        }
    }

    /** Raw gains before [TransitionSpec.mixAmount] is applied. */
    internal fun gains(position: Float): Pair<Float, Float> {
        val t = position.coerceIn(0f, 1f)
        if (this == EQUAL_POWER) {
            return cos(t * HalfPi) to sin(t * HalfPi)
        }
        val eased = ease(t)
        return (1f - eased) to eased
    }

    companion object {
        private const val HalfPi = 1.5707963268f
        private const val ExponentialK = 3f

        fun fromName(name: String?): TransitionCurve =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SMOOTH
    }
}
