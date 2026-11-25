package com.example.musicfy.player.video

import com.example.musicfy.player.ResolvedStream
import com.example.musicfy.util.currentTimeMillis

/** The video found for a song, plus the stream that was resolved for it. */
data class OfficialMusicVideo(
    val videoId: String,
    val streams: ResolvedStream,
)

/**
 * A resolved stream is treated as stale a minute early, as upstream does, so playback does not
 * start on a link that dies halfway through. Streams without an expiry are considered fresh.
 */
val ResolvedStream.isExpired: Boolean
    get() {
        val expiresAt = expiresAtMs ?: return false
        return currentTimeMillis() > expiresAt - STREAM_EXPIRY_SKEW_MS
    }

private const val STREAM_EXPIRY_SKEW_MS = 60_000L
