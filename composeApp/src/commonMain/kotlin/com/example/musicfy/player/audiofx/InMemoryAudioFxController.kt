// InMemoryAudioFxController.kt

package com.example.musicfy.player.audiofx

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Portable in-memory [AudioFxController]. It owns no audio engine: an optional
 * [AudioFxProcessor] is only notified of snapshots, which is where an iOS
 * AVFoundation implementation can attach later.
 */
@Stable
class InMemoryAudioFxController(
    initialState: AudioFxMixerState = AudioFxMixerState(),
    private val processor: AudioFxProcessor? = null,
) : AudioFxController {
    private val _state = MutableStateFlow(initialState.normalized())

    override val state: StateFlow<AudioFxMixerState> get() = _state.asStateFlow()

    private fun publish(transform: (AudioFxMixerState) -> AudioFxMixerState) {
        // StateFlow writes are atomic; UI callers are single-threaded on Kotlin/Native.
        // An attached processor is notified after the snapshot is published.
        val next = transform(_state.value).normalized()
        _state.value = next
        processor?.onStateChanged(next)
    }

    override fun setEnabled(enabled: Boolean) = publish { it.withEnabled(enabled) }

    override fun toggleEnabled() = publish { it.withEnabled(!it.enabled) }

    override fun selectPreset(preset: AudioFxPreset) = publish { it.withPreset(preset) }

    override fun setBandGain(index: Int, gainDb: Float) = publish { it.withBandGain(index, gainDb) }

    override fun setSendLevel(send: AudioFxSend, level: Float) = publish { it.withSendLevel(send, level) }

    override fun setEffectEnabled(effect: AudioFxEffect, enabled: Boolean) =
        publish { it.withEffectEnabled(effect, enabled) }

    override fun reset() = publish { it.reset() }

    /** Release an attached native sink when the hosting screen/container dies. */
    fun detachProcessor() {
        processor?.release()
    }
}
