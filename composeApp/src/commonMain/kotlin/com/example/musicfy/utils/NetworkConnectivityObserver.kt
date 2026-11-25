package com.example.musicfy.utils

import com.example.musicfy.network.createPlatformHttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get

/**
 * KMP replacement for Android's `ConnectivityManager`-backed observer.
 *
 * iOS has no common Main thread connectivity callback, so the port probes a
 * lightweight endpoint with the same Darwin HTTP engine used by the providers.
 * A failed probe is treated as "offline"; provider errors are still caught.
 */
class NetworkConnectivityObserver {
    private val probeClient by lazy {
        createPlatformHttpClient().config {
            expectSuccess = true
            install(HttpTimeout) {
                connectTimeoutMillis = 2_000
                requestTimeoutMillis = 3_000
            }
        }
    }

    suspend fun isCurrentlyConnected(): Boolean = runCatching {
        probeClient.get("https://music.youtube.com/generate_204").status.value in 200..399
    }.getOrDefault(false)
}
