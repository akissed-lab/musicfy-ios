// StreamResolver.kt

package com.example.musicfy.player

import androidx.compose.runtime.Immutable
import com.example.musicfy.player.model.PlaybackTrack

/** Replaceable seam between playback and WP5/server stream resolution. */
interface StreamResolver {
    suspend fun resolve(track: PlaybackTrack): ResolvedStream
}

/** A playable URL plus the hints a native engine needs to open it. */
@Immutable
data class ResolvedStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val drm: DrmHint? = null,
    val expiresAtMs: Long? = null,
)

/**
 * Platform-neutral DRM contract. Widevine/ClearKey have no AVFoundation path and are reserved for
 * a server/proxy conversion; FairPlay is what an iOS engine will eventually consume.
 */
sealed interface DrmHint {
    @Immutable
    data class Widevine(
        val licenseUrl: String,
        val initData: List<String> = emptyList(),
        val headers: Map<String, String> = emptyMap(),
    ) : DrmHint

    @Immutable
    data class ClearKey(val key: String, val keyId: String? = null) : DrmHint

    @Immutable
    data class FairPlay(
        val licenseUrl: String,
        val certificateUrl: String? = null,
        val assetId: String? = null,
        val headers: Map<String, String> = emptyMap(),
    ) : DrmHint
}
