// AudioFxProcessor.kt

package com.example.musicfy.player.audiofx

/**
 * Future native sink for the mixer state.
 *
 * Implementations live in platform source sets (an `iosMain` AVFoundation
 * adapter, for example). Nothing in commonMain performs DSP, loads C++/EEL2 or
 * talks to JNI: the processor only receives the same immutable snapshot the
 * screen renders.
 */
interface AudioFxProcessor {
    val name: String

    /** Called after every controller mutation. Implementations must not mutate [state]. */
    fun onStateChanged(state: AudioFxMixerState)

    /** Called when the processor is detached from its controller. */
    fun release()
}
