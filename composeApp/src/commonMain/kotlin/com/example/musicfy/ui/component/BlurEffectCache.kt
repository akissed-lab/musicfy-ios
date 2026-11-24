package com.example.musicfy.ui.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Common-safe replacement for the Android `RenderEffect` cache.
 *
 * The upstream file clamped very large radii on API <= 34, quantised them so
 * Skia did not allocate a native filter for every pixel value, and returned
 * `null` for radii too small to matter. None of those platform APIs exist in
 * `commonMain`, but the same policy is still useful to callers which build a
 * [Modifier.blur] radius from scroll progress. This object therefore keeps the
 * public entry points and returns the effective, quantised radius instead of
 * an Android render effect.
 */
object BlurEffectCache {
    /** Upstream's safe ceiling for a single-pass blur on older Android versions. */
    const val MaxSafeRadius: Float = 54f

    private val cache = mutableMapOf<Int, Float>()

    /**
     * The radius a blur actually uses. Large radii are clamped unless
     * [allowLargeRadius] is explicitly requested.
     */
    fun effectiveRadius(radius: Float, allowLargeRadius: Boolean = false): Float =
        if (allowLargeRadius) radius.coerceAtLeast(0f) else radius.coerceAtLeast(0f).coerceAtMost(MaxSafeRadius)

    /** [radius] rounded to the step [get] caches by, so keys and returned radii agree. */
    fun quantize(radius: Float): Float {
        val value = radius.coerceAtLeast(0f)
        val quantum = quantum(value)
        return (value / quantum).roundToInt().coerceAtLeast(1) * quantum
    }

    /**
     * Returns a cached, quantised radius, or `null` when no blur is needed.
     * [tileMode] is retained for upstream call-site compatibility; the common
     * blur has no tile-mode parameter, so it only contributes to the cache key.
     */
    fun get(
        radius: Float,
        tileMode: GlassTileMode = GlassTileMode.CLAMP,
        allowLargeRadius: Boolean = false,
    ): Float? {
        val effective = effectiveRadius(radius, allowLargeRadius)
        if (radius <= 0.5f || effective <= 0.5f) return null
        val key = cacheKey(effective, tileMode)
        return cache.getOrPut(key) { quantize(effective) }
    }

    /** Upstream name kept for call sites which built a native effect. */
    fun getNative(
        radius: Float,
        tileMode: GlassTileMode = GlassTileMode.CLAMP,
    ): Float? = get(radius, tileMode)

    /** Convenience for [androidx.compose.ui.draw.blur], which takes a [Dp]. */
    fun blurDp(radius: Float, allowLargeRadius: Boolean = false): Dp =
        (get(radius, GlassTileMode.CLAMP, allowLargeRadius) ?: 0f).dp

    /** Clears quantised radii. Safe to call from a composition disposal effect. */
    fun clear() {
        cache.clear()
    }

    private fun quantum(radius: Float): Float = when {
        radius <= 16f -> 0.5f
        radius <= 32f -> 1f
        radius <= 64f -> 2f
        else -> 4f
    }

    private fun cacheKey(radius: Float, tileMode: GlassTileMode): Int {
        val quantum = quantum(radius)
        val step = (radius / quantum).roundToInt().coerceAtLeast(1)
        return (step * 100) + (quantum * 10).toInt() + tileMode.ordinal
    }
}
