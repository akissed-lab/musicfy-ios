// AudioFxController.kt

package com.example.musicfy.player.audiofx

import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the Audio FX Mixer UI.
 *
 * The shape mirrors [com.example.musicfy.player.PlaybackController]: commands
 * are non-suspend and state is exposed as a [StateFlow], so screens can simply
 * `collectAsState()` and a native engine can observe the same values later.
 */
interface AudioFxController {
    val state: StateFlow<AudioFxMixerState>

    fun setEnabled(enabled: Boolean)
    fun toggleEnabled()

    fun selectPreset(preset: AudioFxPreset)
    fun setBandGain(index: Int, gainDb: Float)

    fun setSendLevel(send: AudioFxSend, level: Float)
    fun setEffectEnabled(effect: AudioFxEffect, enabled: Boolean)

    /** Restore the default curve/sends/effects without changing [AudioFxMixerState.enabled]. */
    fun reset()
}
