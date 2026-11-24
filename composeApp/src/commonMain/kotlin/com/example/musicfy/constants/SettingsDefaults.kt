// SettingsDefaults.kt
//
// Defaults and limits extracted from upstream PreferenceKeys.kt, the settings
// screens and the playback/scrobble services. Only values that ported or
// settings code actually needs are included.

package com.example.musicfy.constants

object SettingsDefaults {
    /** Content language / country / suggestion-region "follow the system" marker. */
    const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"

    /** Minimum gap between two full library syncs (milliseconds). */
    const val SYNC_COOLDOWN = 30 * 60L

    // pause / play / skip fades
    const val TransportFadeEnabledDefault = true
    const val TransportFadeDefaultSeconds = 2f
    const val TransportFadeMinSeconds = 0.5f
    const val TransportFadeMaxSeconds = 10f

    /** The stored value must reach this generation for the one-off wipe to be done. */
    const val LyricsCacheWipeGeneration = 1

    // failed stream: seconds before skipping, clamped to 5..20 in MusicService
    const val StreamFailureTimeoutDefault = 6
    const val StreamFailureTimeoutMin = 5
    const val StreamFailureTimeoutMax = 20

    // caches, in MiB
    const val MaxImageCacheSizeDefault = 512
    const val MaxSongCacheSizeDefault = 1024

    // volume and UI scale
    const val PlayerVolumeDefault = 1f
    const val BlurStrengthPxDefault = 25f
    const val DensityScaleDefault = 1.0f

    // scrobbling (ScrobbleTimer defaults)
    const val ScrobbleDelayPercentDefault = 0.5f
    const val ScrobbleMinSongDurationDefault = 30
    const val ScrobbleDelaySecondsDefault = 240

    // crossfade duration by mode, with the same clamps MusicService uses
    const val CrossfadeDurationDefaultSeconds = 5f
    const val CrossfadeMinSeconds = 1f
    const val CrossfadeMaxSeconds = 30f
    const val CrossmixDurationDefaultSeconds = 12f
    const val CrossmixMinSeconds = 8f
    const val CrossmixMaxSeconds = 20f
}

// Upstream top-level names, kept as aliases for code ported verbatim.
const val SYSTEM_DEFAULT: String = SettingsDefaults.SYSTEM_DEFAULT
const val SYNC_COOLDOWN: Long = SettingsDefaults.SYNC_COOLDOWN
const val TransportFadeDefaultSeconds: Float = SettingsDefaults.TransportFadeDefaultSeconds
const val TransportFadeMinSeconds: Float = SettingsDefaults.TransportFadeMinSeconds
const val TransportFadeMaxSeconds: Float = SettingsDefaults.TransportFadeMaxSeconds
const val LyricsCacheWipeGeneration: Int = SettingsDefaults.LyricsCacheWipeGeneration
