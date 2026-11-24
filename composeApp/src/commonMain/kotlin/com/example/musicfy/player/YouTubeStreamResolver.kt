package com.example.musicfy.player

import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.TrackSource
import com.music.innertube.YouTubePlayer

/**
 * Default [StreamResolver] for the common playback layer.
 *
 * Local and already-resolved direct sources need no network; remote video ids go through the
 * VISIONOS-style Innertube player request in [YouTubePlayer]. Any failure to resolve is thrown
 * with a readable message for the player's error UI.
 */
class YouTubeStreamResolver : StreamResolver {
    override suspend fun resolve(track: PlaybackTrack): ResolvedStream =
        when (val source = track.source) {
            is TrackSource.Local -> ResolvedStream(url = source.uri)

            is TrackSource.Direct -> ResolvedStream(
                url = source.url,
                headers = source.headers,
                drm = source.drm,
            )

            is TrackSource.Remote -> {
                val stream = YouTubePlayer.resolve(source.videoId).getOrThrow()
                stream.copy(
                    // Explicit per-track headers win over the defaults from the player request.
                    headers = stream.headers + source.headers,
                    drm = source.drm ?: stream.drm,
                )
            }

            else -> error("Unsupported track source for ${track.mediaId}: $source")
        }
}
