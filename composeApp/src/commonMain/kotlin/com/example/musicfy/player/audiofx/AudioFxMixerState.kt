// AudioFxMixerState.kt
//
// Portable model of the upstream "Audio FX Mixer" (PR #407). The native mixer,
// EEL2/LiveProg scripts and JNI are deliberately not ported: this is the state
// shape the UI and a future iOS/AVFoundation [AudioFxProcessor] can share.

package com.example.musicfy.player.audiofx

import androidx.compose.runtime.Immutable

/** The fixed ten-band layout used by the mixer UI, from sub-bass to air. */
enum class AudioFxBand(val frequencyHz: Int, val label: String) {
    B31(31, "31"),
    B62(62, "62"),
    B125(125, "125"),
    B250(250, "250"),
    B500(500, "500"),
    B1K(1_000, "1k"),
    B2K(2_000, "2k"),
    B4K(4_000, "4k"),
    B8K(8_000, "8k"),
    B16K(16_000, "16k"),
}

/** A built-in ten-band curve. Keys are stable so a host can persist selections. */
enum class AudioFxPreset(
    val key: String,
    val label: String,
    val gainsDb: List<Float>,
) {
    FLAT("flat", "Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    BASS_BOOST("bass_boost", "Bass Boost", listOf(6f, 5f, 4f, 2f, 0f, -1f, -1f, 0f, 0f, 0f)),
    VOCAL("vocal", "Vocal", listOf(-3f, -2f, -1f, 0f, 2f, 4f, 5f, 4f, 2f, 0f)),
    ROCK("rock", "Rock", listOf(5f, 4f, 2f, -1f, -2f, 1f, 3f, 4f, 4f, 3f)),
    POP("pop", "Pop", listOf(-1f, 0f, 1f, 3f, 4f, 3f, 1f, 0f, 0f, -1f)),
    JAZZ("jazz", "Jazz", listOf(3f, 2f, 1f, 2f, 0f, 1f, 2f, 3f, 3f, 2f)),
    ACOUSTIC("acoustic", "Acoustic", listOf(2f, 1f, 1f, 2f, 3f, 3f, 2f, 1f, 0f, -1f)),
    ELECTRONIC("electronic", "Electronic", listOf(6f, 5f, 3f, 1f, -1f, 2f, 3f, 2f, 4f, 5f)),
    ;

    companion object {
        val Default: AudioFxPreset = FLAT

        fun fromKey(key: String?): AudioFxPreset? = entries.firstOrNull { it.key == key }
    }
}

/** Post-fader destinations of the simplified mixer (master plus two aux sends). */
enum class AudioFxSend(
    val key: String,
    val label: String,
    val defaultLevel: Float,
) {
    MASTER("master", "Master", 1f),
    REVERB("reverb", "Reverb", 0.25f),
    SPATIAL("spatial", "Spatial", 0f),
    ;

    companion object {
        fun defaultLevels(): Map<AudioFxSend, Float> = entries.associateWith { it.defaultLevel }

        fun fromKey(key: String?): AudioFxSend? = entries.firstOrNull { it.key == key }
    }
}

/** Per-effect bypasses, kept apart from the band curve so they can be toggled live. */
enum class AudioFxEffect(
    val key: String,
    val label: String,
    val defaultEnabled: Boolean,
) {
    BASS_BOOST("bass_boost", "Bass boost", false),
    REVERB("reverb", "Reverb", false),
    LOUDNESS("loudness", "Loudness", false),
    SPATIAL("spatial", "Spatial", false),
    COMPRESSOR("compressor", "Compressor", false),
    ;

    companion object {
        fun defaults(): Map<AudioFxEffect, Boolean> = entries.associateWith { it.defaultEnabled }

        fun fromKey(key: String?): AudioFxEffect? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Immutable snapshot published by an [AudioFxController].
 *
 * `activePreset` is null after a fader is moved by hand, which lets the UI show
 * "Custom" without storing a second copy of the curve.
 */
@Immutable
data class AudioFxMixerState(
    val enabled: Boolean = false,
    val bandGainsDb: List<Float> = AudioFxPreset.FLAT.gainsDb,
    val activePreset: AudioFxPreset? = AudioFxPreset.FLAT,
    val sendLevels: Map<AudioFxSend, Float> = AudioFxSend.defaultLevels(),
    val effectEnabled: Map<AudioFxEffect, Boolean> = AudioFxEffect.defaults(),
) {
    fun gain(index: Int): Float = bandGainsDb.getOrNull(index) ?: 0f

    fun sendLevel(send: AudioFxSend): Float = sendLevels[send] ?: send.defaultLevel

    fun isEffectEnabled(effect: AudioFxEffect): Boolean = effectEnabled[effect] ?: effect.defaultEnabled

    fun withEnabled(enabled: Boolean): AudioFxMixerState = copy(enabled = enabled)

    fun withPreset(preset: AudioFxPreset): AudioFxMixerState = copy(
        bandGainsDb = preset.gainsDb.toList(),
        activePreset = preset,
    )

    fun withBandGain(index: Int, gainDb: Float): AudioFxMixerState {
        if (index !in 0 until BandCount) return this
        val gains = bandGainsDb.toMutableList()
        while (gains.size < BandCount) gains.add(0f)
        gains[index] = gainDb.coerceIn(MinGainDb, MaxGainDb)
        return copy(bandGainsDb = gains.take(BandCount), activePreset = null)
    }

    fun withSendLevel(send: AudioFxSend, level: Float): AudioFxMixerState = copy(
        sendLevels = sendLevels + (send to level.coerceIn(MinSendLevel, MaxSendLevel)),
    )

    fun withEffectEnabled(effect: AudioFxEffect, enabled: Boolean): AudioFxMixerState = copy(
        effectEnabled = effectEnabled + (effect to enabled),
    )

    /** Restore the default curve, sends and bypasses, leaving the master switch alone. */
    fun reset(): AudioFxMixerState = copy(
        bandGainsDb = AudioFxPreset.FLAT.gainsDb.toList(),
        activePreset = AudioFxPreset.FLAT,
        sendLevels = AudioFxSend.defaultLevels(),
        effectEnabled = AudioFxEffect.defaults(),
    )

    /** Defensive copy used at controller boundaries: fixes array length and fills missing keys. */
    fun normalized(): AudioFxMixerState = copy(
        bandGainsDb = List(BandCount) { index -> gain(index).coerceIn(MinGainDb, MaxGainDb) },
        sendLevels = AudioFxSend.entries.associateWith { send -> sendLevel(send) },
        effectEnabled = AudioFxEffect.entries.associateWith { effect -> isEffectEnabled(effect) },
    )

    companion object {
        const val BandCount: Int = 10
        const val MinGainDb: Float = -12f
        const val MaxGainDb: Float = 12f
        const val MinSendLevel: Float = 0f
        const val MaxSendLevel: Float = 1f
    }
}
