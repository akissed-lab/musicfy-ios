@file:OptIn(ExperimentalSerializationApi::class)

package com.music.innertube

import com.example.musicfy.network.createPlatformHttpClient
import com.example.musicfy.player.ResolvedStream
import com.example.musicfy.util.currentTimeMillis
import com.music.innertube.models.PlayerBody
import com.music.innertube.models.PlayerStreamFormat
import com.music.innertube.models.PlayerStreamResponse
import com.music.innertube.models.YouTubeClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Stream-only slice of Innertube for the common playback layer.
 *
 * Upstream uses VISIONOS as its main client because it reports direct, un-ciphered audio URLs
 * for every audio itag without extra token minting. There is deliberately no cipher
 * deobfuscation or platform-specific extraction path here: a ciphered response is reported
 * as a readable error instead.
 */
object YouTubePlayer {
    /** The googlevideo URL's `c=` parameter names the VISIONOS client, so use its origin. */
    private const val STREAM_ORIGIN = "https://www.youtube.com"
    private const val STREAM_REFERER = "$STREAM_ORIGIN/"
    private const val STREAM_ACCEPT = "*/*"

    /** VISIONOS constants from upstream `YouTubeClient.VISIONOS`, defined locally. */
    private val PLAYER_CLIENT = YouTubeClient(
        clientName = "VISIONOS",
        clientVersion = "0.1",
        clientId = "101",
        userAgent =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/18.0 Safari/605.1.15",
        osName = "visionOS",
        osVersion = "1.3.21O771",
        deviceMake = "Apple",
        deviceModel = "RealityDevice14,1",
    )

    private val httpClient = createPlatformHttpClient().config {
        expectSuccess = true

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                    encodeDefaults = true
                },
            )
        }

        install(ContentEncoding) {
            gzip(0.9f)
            deflate(0.8f)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }

        defaultRequest {
            url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
            header("Accept", "application/json")
            header("Accept-Language", "en-US,en;q=0.9")
            header("Cache-Control", "no-cache")
        }
    }

    /**
     * Resolve [videoId] to a directly playable audio URL.
     *
     * [visitorData] is optional; upstream notes that VISIONOS returns `UNPLAYABLE` without one,
     * so callers that have a guest visitor id should pass it along.
     */
    suspend fun resolve(videoId: String, visitorData: String? = null): Result<ResolvedStream> =
        runCatching {
            if (YouTube.offline) error("Offline")

            val response = withRetry {
                httpClient.post("player") {
                    ytClient(PLAYER_CLIENT, visitorData)
                    setBody(
                        PlayerBody(
                            context = PLAYER_CLIENT.toContext().let { context ->
                                if (visitorData.isNullOrBlank()) context
                                else context.copy(
                                    client = context.client.copy(visitorData = visitorData),
                                )
                            },
                            videoId = videoId,
                        ),
                    )
                }.body<PlayerStreamResponse>()
            }

            val status = response.playabilityStatus
            if (status?.status != "OK") {
                val reason = status?.reason?.takeIf { it.isNotBlank() }
                error(
                    "YouTube cannot play $videoId (${status?.status ?: "no status"});" +
                        (reason?.let { " $it" }.orEmpty()),
                )
            }

            val formats = response.streamingData?.adaptiveFormats.orEmpty()
            val audioFormats = formats.filter {
                it.mimeType?.startsWith("audio/", ignoreCase = true) == true
            }
            val candidates = audioFormats.ifEmpty { formats }

            // A direct URL wins over a ciphered format even if the latter has a higher bitrate:
            // ciphered URLs cannot be opened by this client.
            val selected = candidates.filter { !it.url.isNullOrBlank() }
                .maxWithOrNull(FORMAT_ORDER)
                ?: throw IllegalStateException(
                    if (candidates.any { !it.signatureCipher.isNullOrBlank() }) {
                        "YouTube returned only signature-ciphered audio URLs for $videoId; " +
                            "this client cannot decrypt them"
                    } else {
                        "No direct audio stream found for $videoId"
                    },
                )

            val url = selected.url?.takeIf { it.isNotBlank() }
                ?: error("No direct audio stream found for $videoId")

            ResolvedStream(
                url = url,
                headers = mapOf(
                    "User-Agent" to PLAYER_CLIENT.userAgent,
                    "Referer" to STREAM_REFERER,
                    "Origin" to STREAM_ORIGIN,
                    "Accept" to STREAM_ACCEPT,
                ),
                expiresAtMs = expiresAtMs(response.streamingData?.expiresInSeconds),
            )
        }

    private fun HttpRequestBuilder.ytClient(client: YouTubeClient, visitorData: String?) {
        contentType(ContentType.Application.Json)
        header("X-Goog-Api-Format-Version", "1")
        header("X-YouTube-Client-Name", client.clientId)
        header("X-YouTube-Client-Version", client.clientVersion)
        header("X-Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
        header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
        visitorData?.takeIf { it.isNotBlank() }?.let { header("X-Goog-Visitor-Id", it) }
        userAgent(client.userAgent)
        parameter("prettyPrint", "false")
    }

    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 500L,
        factor: Double = 2.0,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxAttempts) throw e
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong()
            }
        }
    }

    /** audio/ formats were already preferred; quality label outranks raw bitrate, as upstream. */
    private val FORMAT_ORDER = compareByDescending<PlayerStreamFormat> { qualityRank(it.audioQuality) }
        .thenByDescending { it.bitrate ?: 0L }
        .thenByDescending { it.itag ?: 0 }

    private fun qualityRank(audioQuality: String?): Int = when {
        audioQuality.isNullOrBlank() -> 0
        audioQuality.contains("HIGH", ignoreCase = true) -> 3
        audioQuality.contains("MEDIUM", ignoreCase = true) -> 2
        audioQuality.contains("LOW", ignoreCase = true) -> 1
        else -> 0
    }

    private fun expiresAtMs(expiresInSeconds: Long?): Long? {
        val seconds = expiresInSeconds?.takeIf { it > 0 } ?: return null
        val now = currentTimeMillis()
        return now + seconds.coerceAtMost((Long.MAX_VALUE - now) / 1000L) * 1000L
    }
}
