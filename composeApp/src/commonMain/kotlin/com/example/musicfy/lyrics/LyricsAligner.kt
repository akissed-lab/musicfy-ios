// LyricsAligner.kt
//
// Port of upstream global + per-line lyrics alignment against vocal activity.
// Character.UnicodeScript (JVM-only) is replaced by code-point ranges.

package com.example.musicfy.lyrics

import com.example.musicfy.lyrics.VocalActivity.Companion.FrameMs
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lines timed lyrics up with where the song is really sung ([VocalActivity]),
 * and works out the offset that puts each line on its singing.
 */
class LyricsAligner {
    class Alignment(
        /** Added to the playback position, the way the song's lyrics offset is. */
        val offsetMs: Int,
        /** 0..1: how sure the match is. */
        val confidence: Float,
        /** How far the timing drifts between the first line and the last, ms; null when steady. */
        val driftMs: Int?,
        val strongLines: Int,
        val totalLines: Int,
    )

    /** Null when there's too little to line up or nothing agrees anywhere. */
    fun align(lines: List<LyricsEntry>, vocals: VocalActivity, maxOffsetMs: Int): Alignment? {
        val expected = expectedSinging(lines) ?: return null
        if (expected.spans.size < MinLines) return null

        val heardVoice = zscore(vocals.presence)
        val heardOnsets = zscore(vocals.onsets)
        val wantedVoice = zscore(expected.presence)
        val wantedOnsets = zscore(expected.onsets)
        val onsetWeight = if (expected.byWord) 0.6f else 0.35f

        val maxShift = maxOffsetMs / FrameMs
        val coarseVoice = downsample(heardVoice); val coarseOnsets = downsample(heardOnsets)
        val coarseWantedVoice = downsample(wantedVoice); val coarseWantedOnsets = downsample(wantedOnsets)
        val coarseMax = maxShift / Coarse
        val coarse = FloatArray(coarseMax * 2 + 1) { i ->
            val shift = i - coarseMax
            correlate(coarseVoice, coarseWantedVoice, shift) +
                onsetWeight * correlate(coarseOnsets, coarseWantedOnsets, shift)
        }
        val coarseBest = coarse.indices.maxBy { coarse[it] }
        val best = coarse[coarseBest]
        if (best <= 0f || abs(coarseBest - coarseMax) >= coarseMax - 1) return null

        val hill = if (expected.byWord) WordHillCoarse else LineHillCoarse
        val runnerUp = coarse.indices
            .filter { abs(it - coarseBest) > hill }
            .maxOfOrNull { coarse[it] } ?: 0f
        val sharpness = ((best - runnerUp) / (abs(best) + 1e-3f)).coerceIn(0f, 1f)
        if (sharpness < MinSharpness) return null

        val center = (coarseBest - coarseMax) * Coarse
        val fine = (center - Coarse - 2..center + Coarse + 2).associateWith { shift ->
            correlate(heardVoice, wantedVoice, shift) +
                onsetWeight * correlate(heardOnsets, wantedOnsets, shift)
        }
        val shift = fine.maxBy { it.value }.key
        val globalOffset = shift * FrameMs + subFrame(fine, shift) - vocals.originMs
        val voiceAgreement = correlate(heardVoice, wantedVoice, shift)

        val lineOnsetWeight = if (expected.byWord) onsetWeight else 1f
        val strongLine = if (expected.byWord) StrongLine else StrongLineByLine
        val lineOffsets = refineLines(
            expected.spans, shift, heardVoice, heardOnsets, wantedVoice, wantedOnsets, lineOnsetWeight,
        ).map { (span, frames, strength) ->
            LineOffset(span, (shift + frames) * FrameMs - vocals.originMs, strength)
        }
        val strong = lineOffsets.filter { it.strength >= strongLine }

        val offset = if (strong.size >= MinStrongLines) weightedMedian(strong) else globalOffset
        val agrees = abs(offset - globalOffset) <= 150f
        val drift = if (strong.size >= MinStrongLines) drift(strong) else null

        val confidence = (
            0.55f * (sharpness / 0.6f).coerceIn(0f, 1f) +
                0.3f * (voiceAgreement / 0.45f).coerceIn(0f, 1f) +
                0.15f * strong.size.toFloat() / lineOffsets.size
            ) * (if (agrees) 1f else 0.75f)

        return Alignment(
            offsetMs = ((offset / 10f).roundToInt() * 10).coerceIn(-maxOffsetMs, maxOffsetMs),
            confidence = confidence.coerceIn(0f, 1f),
            driftMs = drift,
            strongLines = strong.size,
            totalLines = lineOffsets.size,
        )
    }

    private class LineSpan(val startMs: Long, val endMs: Long)

    private class Expected(
        val presence: FloatArray,
        val onsets: FloatArray,
        val spans: List<LineSpan>,
        val byWord: Boolean,
    )

    private fun expectedSinging(lines: List<LyricsEntry>): Expected? {
        val sung = lines.withIndex().filter { it.value.text.isNotBlank() }
        if (sung.isEmpty()) return null
        val byWord = sung.count { !it.value.words.isNullOrEmpty() } * 2 >= sung.size

        val spans = ArrayList<LineSpan>()
        val words = ArrayList<Triple<Long, Long, Float>>()
        val starts = ArrayList<Pair<Long, Float>>()
        for ((index, line) in sung) {
            val weight = if (line.isBackground) 0.5f else 1f
            val timed = line.words?.filter { it.text.isNotBlank() && it.endTime > it.startTime }
            if (!timed.isNullOrEmpty()) {
                var previousEnd = -1L
                for (word in timed) {
                    val start = (word.startTime * 1_000).toLong()
                    val end = (word.endTime * 1_000).toLong()
                    val from = if (previousEnd >= 0 && start - previousEnd in 1..LegatoGapMs) previousEnd else start
                    words += Triple(from, end, weight)
                    starts += start to weight
                    previousEnd = end
                }
                spans += LineSpan((timed.first().startTime * 1_000).toLong(), (timed.last().endTime * 1_000).toLong())
            } else {
                val start = line.time
                val next = sung.firstOrNull { it.index > index }?.value?.time ?: (start + MaxLineMs)
                val guess = (syllables(line.text) * MsPerSyllable).coerceIn(MinLineMs, MaxLineMs)
                val end = min(next - 50, start + guess).coerceAtLeast(start + 300)
                words += Triple(start, end, weight)
                starts += start to weight * 1.5f
                spans += LineSpan(start, end)
            }
        }

        val endMs = (words.maxOfOrNull { it.second } ?: 0L) + 2_000L
        val size = (endMs / FrameMs).toInt() + 1
        val presence = FloatArray(size)
        for ((from, to, weight) in words) {
            val a = (from / FrameMs).toInt().coerceIn(0, size)
            val b = (to / FrameMs).toInt().coerceIn(0, size)
            val fade = if (byWord) 0 else FadeFrames
            for (g in a until b) {
                val tail = if (fade > 0) ((b - g).toFloat() / fade).coerceAtMost(1f) else 1f
                presence[g] = max(presence[g], weight * tail)
            }
        }
        val impulses = FloatArray(size)
        for ((at, weight) in starts) {
            val g = (at / FrameMs).toInt()
            if (g in 0 until size) impulses[g] = max(impulses[g], weight)
        }
        val onsets = gaussianBlur(impulses, sigma = if (byWord) 3f else 8f)
        return Expected(presence, onsets, spans, byWord)
    }

    /** Roughly how many syllables a line sings. */
    private fun syllables(text: String): Int {
        var count = 0
        var inVowel = false
        for (ch in text) {
            val cp = ch.code
            when {
                cp in 0x3040..0x30FF || cp in 0x3400..0x9FFF || cp in 0x1100..0x11FF || cp in 0xAC00..0xD7AF -> {
                    count++
                    inVowel = false
                }
                ch.lowercaseChar() in Vowels -> {
                    if (!inVowel) count++
                    inVowel = true
                }
                else -> inVowel = false
            }
        }
        return count.coerceAtLeast(1)
    }

    private data class LineFit(val span: LineSpan, val frames: Int, val strength: Float)
    private class LineOffset(val span: LineSpan, val offsetMs: Float, val strength: Float)

    /** Best position for each line within ±[LineSlackFrames], chosen together. */
    private fun refineLines(
        spans: List<LineSpan>,
        shift: Int,
        heardVoice: FloatArray,
        heardOnsets: FloatArray,
        wantedVoice: FloatArray,
        wantedOnsets: FloatArray,
        onsetWeight: Float,
    ): List<LineFit> {
        val states = LineSlackFrames * 2 + 1
        val scores = spans.map { span ->
            val from = (span.startMs / FrameMs).toInt() - LineMarginFrames
            val to = (span.endMs / FrameMs).toInt() + LineMarginFrames
            zscore(FloatArray(states) { s ->
                val lineShift = shift + s - LineSlackFrames
                correlateWindow(heardVoice, wantedVoice, lineShift, from, to) +
                    onsetWeight * correlateWindow(heardOnsets, wantedOnsets, lineShift, from, to)
            })
        }

        val total = Array(spans.size) { FloatArray(states) }
        val from = Array(spans.size) { IntArray(states) }
        scores[0].copyInto(total[0])
        for (i in 1 until spans.size) {
            for (s in 0 until states) {
                var bestValue = Float.NEGATIVE_INFINITY
                var bestFrom = s
                for (p in 0 until states) {
                    val value = total[i - 1][p] - JumpCost * abs(s - p)
                    if (value > bestValue) { bestValue = value; bestFrom = p }
                }
                total[i][s] = bestValue + scores[i][s]
                from[i][s] = bestFrom
            }
        }
        val chosen = IntArray(spans.size)
        chosen[spans.lastIndex] = total.last().indices.maxBy { total.last()[it] }
        for (i in spans.lastIndex downTo 1) chosen[i - 1] = from[i][chosen[i]]

        return spans.mapIndexed { i, span ->
            LineFit(span, frames = chosen[i] - LineSlackFrames, strength = scores[i][chosen[i]])
        }
    }

    /** Weighted median of the strong lines' offsets. */
    private fun weightedMedian(lines: List<LineOffset>): Float {
        val sorted = lines.sortedBy { it.offsetMs }
        val half = sorted.sumOf { it.strength.toDouble() } / 2
        var running = 0.0
        for (line in sorted) {
            running += line.strength
            if (running >= half) return line.offsetMs
        }
        return sorted.last().offsetMs
    }

    /** Weighted linear drift across the song, when steady enough to be real. */
    private fun drift(lines: List<LineOffset>): Int? {
        val weights = lines.map { it.strength.toDouble() }
        val xs = lines.map { it.span.startMs / 1_000.0 }
        val ys = lines.map { it.offsetMs.toDouble() }
        val totalWeight = weights.sum().takeIf { it > 0 } ?: return null
        val meanX = xs.indices.sumOf { weights[it] * xs[it] } / totalWeight
        val meanY = ys.indices.sumOf { weights[it] * ys[it] } / totalWeight
        val spreadX = xs.indices.sumOf { weights[it] * (xs[it] - meanX) * (xs[it] - meanX) }
        if (spreadX <= 1e-6) return null
        val slope = xs.indices.sumOf { weights[it] * (xs[it] - meanX) * (ys[it] - meanY) } / spreadX
        val spreadY = ys.indices.sumOf { weights[it] * (ys[it] - meanY) * (ys[it] - meanY) }
        val residual = ys.indices.sumOf {
            val fitted = meanY + slope * (xs[it] - meanX)
            weights[it] * (ys[it] - fitted) * (ys[it] - fitted)
        }
        val explained = if (spreadY > 1e-6) 1.0 - residual / spreadY else 0.0
        val across = slope * ((xs.maxOrNull() ?: 0.0) - (xs.minOrNull() ?: 0.0))
        return if (abs(across) >= MinDriftMs && explained >= 0.5) across.roundToInt() else null
    }

    /** The mean of heard[f] × wanted[f + shift] wherever both exist. */
    private fun correlate(heard: FloatArray, wanted: FloatArray, shift: Int): Float {
        val start = max(0, -shift)
        val end = min(heard.size, wanted.size - shift)
        if (end <= start) return 0f
        var sum = 0.0
        for (f in start until end) sum += heard[f] * wanted[f + shift]
        return (sum / (end - start)).toFloat()
    }

    /** [correlate] over lyric frames [from] until [to] only. */
    private fun correlateWindow(
        heard: FloatArray,
        wanted: FloatArray,
        shift: Int,
        from: Int,
        to: Int,
    ): Float {
        val start = maxOf(from, 0, shift)
        val end = minOf(to, wanted.size, heard.size + shift)
        if (end <= start) return 0f
        var sum = 0.0
        for (g in start until end) sum += heard[g - shift] * wanted[g]
        return (sum / (end - start)).toFloat()
    }

    /** Parabola through the best score and its neighbours, in ms. */
    private fun subFrame(scores: Map<Int, Float>, peak: Int): Float {
        val left = scores[peak - 1] ?: return 0f
        val middle = scores[peak] ?: return 0f
        val right = scores[peak + 1] ?: return 0f
        val curve = left - 2f * middle + right
        if (curve >= 0f) return 0f
        return (0.5f * (left - right) / curve).coerceIn(-0.5f, 0.5f) * FrameMs
    }

    private fun zscore(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        val mean = values.sum() / values.size
        var variance = 0.0
        for (v in values) variance += (v - mean) * (v - mean)
        val deviation = sqrt(variance / values.size).toFloat().takeIf { it > 1e-6f }
            ?: return FloatArray(values.size)
        return FloatArray(values.size) { (values[it] - mean) / deviation }
    }

    private fun downsample(values: FloatArray): FloatArray = FloatArray(values.size / Coarse) { i ->
        var sum = 0f
        for (j in 0 until Coarse) sum += values[i * Coarse + j]
        sum / Coarse
    }

    private fun gaussianBlur(values: FloatArray, sigma: Float): FloatArray {
        val radius = (sigma * 3).toInt()
        val kernel = FloatArray(radius * 2 + 1) {
            exp(-((it - radius) * (it - radius)) / (2f * sigma * sigma))
        }
        val out = FloatArray(values.size)
        for (i in values.indices) {
            if (values[i] == 0f) continue
            for (k in kernel.indices) {
                val at = i + k - radius
                if (at in out.indices) out[at] = max(out[at], values[i] * kernel[k])
            }
        }
        return out
    }

    private companion object {
        const val MinLines = 4
        const val MinStrongLines = 3
        const val Coarse = 4
        const val WordHillCoarse = 10
        const val LineHillCoarse = 40
        const val MinSharpness = 0.25f
        const val LineSlackFrames = 40
        const val LineMarginFrames = 30
        const val JumpCost = 0.04f
        const val StrongLine = 2.0f
        const val StrongLineByLine = 1.6f
        const val MinDriftMs = 250
        const val LegatoGapMs = 120L
        const val MsPerSyllable = 260L
        const val MinLineMs = 700L
        const val MaxLineMs = 7_000L
        const val FadeFrames = 20
        const val Vowels = "aeiouyàáâãäåæèéêëìíîïòóôõöøùúûüýÿąęėīįōőūűų"
    }
}
