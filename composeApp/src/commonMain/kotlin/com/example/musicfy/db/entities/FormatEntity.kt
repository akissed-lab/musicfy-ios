package com.example.musicfy.db.entities

import androidx.compose.runtime.Immutable

@Immutable
data class FormatEntity(
    val id: String,
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double? = null,
    /** Deprecated upstream: playbackTrackingUrl should be retrieved from a fresh player request. */
    val playbackUrl: String?,
)
