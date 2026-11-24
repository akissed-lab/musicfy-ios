package com.example.musicfy.network

import io.ktor.client.HttpClient

/**
 * KMP replacement for the Android-specific `HttpClient(CIO)` / `HttpClient(OkHttp)`
 * calls in every provider module. The iOS actual uses the Darwin engine; adding
 * other targets later only requires another actual implementation.
 */
expect fun createPlatformHttpClient(): HttpClient
