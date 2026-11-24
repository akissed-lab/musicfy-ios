package com.music.directlyrics

import com.example.musicfy.util.currentTimeMillis
import com.music.directlyrics.models.DeezerAuthResponse
import com.music.directlyrics.models.DeezerLyricsResponse
import com.music.directlyrics.models.DeezerSearchResponse
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** deezer asked directly with an anonymous login, lyrics come word-timed when deezer has that */
@OptIn(ExperimentalEncodingApi::class)
object Deezer {

    private val tokenLock = Mutex()
    private var token: String? = null
    private var tokenExpiresAt = 0L

    suspend fun getLyrics(title: String, artist: String, duration: Int): Result<String> = runCatching {
        val trackId = search(title, artist).bestMatch(title, artist, duration)
            ?: error("No matching Deezer track")
        val lyrics = fetchLyrics(trackId) ?: error("Deezer has no lyrics for this track")
        val lines = lyrics.synchronizedWordByWordLines.orEmpty().map { line ->
            TimedLine(
                startMs = line.start,
                text = joinWords(line.words.map { it.word }),
                words = line.words.map { TimedWord(it.word, it.start, it.end) },
            )
        }.ifEmpty {
            lyrics.synchronizedLines.orEmpty().map { TimedLine(it.milliseconds, it.line.trim()) }
        }
        when {
            lines.hasLyrics() -> lines.toLrc()
            !lyrics.text.isNullOrBlank() -> lyrics.text.replace("\r\n", "\n").trim()
            else -> error("Deezer has no lyrics for this track")
        }
    }

    private suspend fun search(title: String, artist: String): List<TrackCandidate<Long>> =
        lyricsHttp.get("https://api.deezer.com/search") {
            parameter("q", "$title $artist")
            parameter("limit", SearchLimit)
        }.body<DeezerSearchResponse>().data.map { track ->
            TrackCandidate(
                titles = listOf(track.title, track.titleShort),
                artists = listOfNotNull(track.artist?.name),
                durationSec = track.duration,
                ref = track.id,
            )
        }

    private suspend fun fetchLyrics(trackId: Long): DeezerLyricsResponse.Lyrics? {
        val query = "query { track(trackId: \"$trackId\") { lyrics { text " +
            "synchronizedLines { milliseconds line } " +
            "synchronizedWordByWordLines { start words { start end word } } } } }"
        return try {
            lyricsHttp.post("https://pipe.deezer.com/api") {
                bearerAuth(accessToken())
                setBody(TextContent(buildJsonObject { put("query", query) }.toString(), ContentType.Application.Json))
            }.body<DeezerLyricsResponse>().data?.track?.lyrics
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // most likely the token went stale early, forget it so the next song logs in again
            tokenLock.withLock { token = null }
            throw e
        }
    }

    // the anonymous token only lasts about six minutes so it is reused until shortly before then
    private suspend fun accessToken(): String {
        tokenLock.withLock {
            token?.takeIf { currentTimeMillis() < tokenExpiresAt }?.let { return it }
            val jwt = lyricsHttp.get("https://auth.deezer.com/login/anonymous") {
                parameter("jo", "p")
                parameter("rto", "c")
            }.body<DeezerAuthResponse>().jwt
            val expiresAt = expiryOf(jwt) ?: (currentTimeMillis() + FallbackTokenLifetimeMs)
            token = jwt
            tokenExpiresAt = expiresAt - TokenRenewMarginMs
            return jwt
        }
    }

    /** when the token stops working, read from its "exp" claim */
    private fun expiryOf(jwt: String): Long? = runCatching {
        val payload = jwt.split('.')[1]
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        val decoded = Base64.UrlSafe.decode(padded).decodeToString()
        LyricsJson.parseToJsonElement(decoded).jsonObject.getValue("exp").jsonPrimitive.long * 1000
    }.getOrNull()

    /** deezer sends bare words so spaces go back between them, except before punctuation and between characters of scripts written without spaces */
    private fun joinWords(words: List<String>): String = buildString {
        words.map { it.trim() }.filter { it.isNotEmpty() }.forEach { word ->
            val attached = word.first() in NoSpaceBefore ||
                (isNotEmpty() && isUnspaced(last()) && isUnspaced(word.first()))
            if (isNotEmpty() && !attached) append(' ')
            append(word)
        }
    }

    /** Kotlin common has no Character.UnicodeScript; these are the four ranges upstream used. */
    private fun isUnspaced(char: Char): Boolean {
        val cp = char.code
        return cp in 0x4E00..0x9FFF || // HAN
            cp in 0x3040..0x309F || // HIRAGANA
            cp in 0x30A0..0x30FF || // KATAKANA
            cp in 0x0E00..0x0E7F // THAI
    }

    private const val SearchLimit = 10
    private const val FallbackTokenLifetimeMs = 5 * 60_000L
    private const val TokenRenewMarginMs = 30_000L
    private const val NoSpaceBefore = ",.!?;:)]}…"
}
