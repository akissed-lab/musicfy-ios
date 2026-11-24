package com.music.innertube.models

import kotlinx.serialization.Serializable

/**
 * Minimal, tolerant slice of the Innertube `player` response used for audio playback.
 *
 * Every field is optional so unknown or partially populated JSON is ignored rather than
 * breaking the resolver. This is deliberately separate from the captions-only
 * [PlayerResponse] above: playback and lyrics read different parts of the same endpoint.
 */
@Serializable
data class PlayerStreamResponse(
    val playabilityStatus: PlayerPlayabilityStatus? = null,
    val streamingData: PlayerStreamingData? = null,
)

@Serializable
data class PlayerPlayabilityStatus(
    val status: String? = null,
    val reason: String? = null,
)

@Serializable
data class PlayerStreamingData(
    val formats: List<PlayerStreamFormat>? = null,
    val adaptiveFormats: List<PlayerStreamFormat>? = null,
    val expiresInSeconds: Long? = null,
)

@Serializable
data class PlayerStreamFormat(
    val url: String? = null,
    val signatureCipher: String? = null,
    val mimeType: String? = null,
    val bitrate: Long? = null,
    val audioQuality: String? = null,
    val contentLength: Long? = null,
    val itag: Int? = null,
)
