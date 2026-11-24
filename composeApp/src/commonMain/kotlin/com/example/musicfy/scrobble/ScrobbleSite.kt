// ScrobbleSite.kt
//
// KMP port of upstream `scrobble/ScrobbleSite.kt`.
// DataStore keys become plain String keys from SettingsKeys; the Android
// provider module (OkHttp/MD5/Last.fm client) is stubbed until it is ported.

package com.example.musicfy.scrobble

import com.example.musicfy.settings.SettingsKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** A signed-in scrobbling account. Kept for parity with the upstream provider module. */
data class ScrobbleSession(val name: String, val key: String)

/**
 * Minimal common-safe stand-in for `com.music.lastfm.Audioscrobbler`.
 *
 * TODO: port the Ktor-backed Last.fm 2.0 client; until then the network flows are
 * empty and [isConfigured] is false because no build-time API credentials exist
 * in commonMain.
 */
class ScrobbleClient(
    val apiRoot: String,
    val authPage: String,
    val apiKey: String,
    val secret: String,
) {
    val isConfigured: Boolean get() = apiKey.isNotEmpty() && secret.isNotEmpty()

    fun authUrl(token: String): String = "$authPage?api_key=$apiKey&token=$token"

    /** TODO: wire requestToken() to the ported client. */
    val tokens: Flow<String> = emptyFlow()

    /** TODO: wire getSession()/scrobble() to the ported client. */
    val sessions: Flow<ScrobbleSession> = emptyFlow()
}

/**
 * The services Musicfy scrobbles to. Both speak Last.fm's API, so one client serves both.
 * Their API keys came from local.properties (or CI secrets) at build time on Android; there is
 * no BuildConfig in commonMain, so [ScrobbleClient.apiKey] is empty until credentials are injected.
 */
enum class ScrobbleSite(
    val label: String,
    /** SettingsKeys entry backing the stored session key. */
    val sessionKey: String,
    /** SettingsKeys entry backing the stored account name. */
    val usernameKey: String,
    /** Scrobbling on or paused, with the account kept either way. */
    val enabledKey: String,
    private val profilePage: String,
    val api: ScrobbleClient,
) {
    LASTFM(
        label = "Last.fm",
        sessionKey = SettingsKeys.LastFMSessionKey,
        usernameKey = SettingsKeys.LastFMUsernameKey,
        enabledKey = SettingsKeys.EnableLastFMScrobblingKey,
        profilePage = "https://www.last.fm/user/",
        api = ScrobbleClient(
            apiRoot = "https://ws.audioscrobbler.com/2.0/",
            authPage = "https://www.last.fm/api/auth/",
            apiKey = "",
            secret = "",
        ),
    ),
    LIBREFM(
        label = "Libre.fm",
        sessionKey = SettingsKeys.LibreFmSessionKey,
        usernameKey = SettingsKeys.LibreFmUsernameKey,
        enabledKey = SettingsKeys.EnableLibreFmScrobblingKey,
        profilePage = "https://libre.fm/user/",
        api = ScrobbleClient(
            apiRoot = "https://libre.fm/2.0/",
            authPage = "https://www.libre.fm/api/auth/",
            apiKey = "",
            secret = "",
        ),
    );

    fun profileUrl(username: String) = profilePage + username
}
