package com.music.paxsenix

import com.example.musicfy.betterlyrics.TTMLParser
import com.example.musicfy.network.createPlatformHttpClient
import com.music.paxsenix.models.LyricsResponse
import com.music.paxsenix.models.LyricText
import com.music.paxsenix.models.SearchResponse
import com.music.paxsenix.models.SearchResult
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import kotlin.math.abs

/** Same version string Android read from PackageManager in `Paxsenix.init(context)`. */
private const val APP_VERSION = "8.1.2 build#1181"

/**
 * Direct port of upstream `providers/paxsenixlyrics/Paxsenix.kt`.
 *
 * Android passed `Context` only to read the app version for the User-Agent;
 * KMP uses the version constant above, so no platform initialization is needed.
 */
object Paxsenix {
    private val httpClient by lazy {
        createPlatformHttpClient().config {
            install(HttpTimeout) {
                requestTimeoutMillis = 5_000
                connectTimeoutMillis = 4_000
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }
            defaultRequest {
                url("https://lyrics.paxsenix.org")
                header("User-Agent", "Musicfy/$APP_VERSION")
            }
            expectSuccess = true
        }
    }

    private val titleCleanupPatterns = listOf(
        Regex(
            """\s*\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            """\s*\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\]""",
            RegexOption.IGNORE_CASE,
        ),
        Regex("""\s*【.*?】"""),
        Regex("""\s*\|.*$"""),
        Regex("""\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\([^)]*\d{4}[^)]*\)""", RegexOption.IGNORE_CASE),
    )

    private val artistSeparators = listOf(
        " & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ",
        " featuring ", " with ",
    )

    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        for (pattern in titleCleanupPatterns) {
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.trim()
    }

    private fun cleanArtist(artist: String): String {
        var cleaned = artist.trim()
        for (separator in artistSeparators) {
            if (cleaned.contains(separator, ignoreCase = true)) {
                cleaned = cleaned.split(separator, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }

    private suspend fun search(query: String): List<SearchResult> = runCatching {
        httpClient.get("/apple-music/search") {
            parameter("q", query)
        }.body<SearchResponse>()
    }.getOrDefault(emptyList())

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String> = runCatching {
        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)

        val searchQueries = buildList {
            add("$cleanedTitle $cleanedArtist")
            add(cleanedTitle)
            if (!album.isNullOrBlank()) add("$cleanedTitle $cleanedArtist $album")
        }

        var allResults: List<Pair<SearchResult, Double>> = emptyList()
        for (query in searchQueries) {
            if (allResults.isEmpty()) {
                val searchResults = search(query)
                if (searchResults.isNotEmpty()) {
                    allResults = scoreAndFilterResults(searchResults, title, artist, duration)
                }
            }
        }

        if (allResults.isEmpty()) error("No tracks found on Paxsenix")

        val candidates = allResults.take(5)
        val scope = CoroutineScope(Dispatchers.Default)
        val jobs = candidates.map { (result, score) ->
            scope.async {
                result to runCatching { fetchLyricsForTrackWithType(result.id) }
                    .getOrDefault("" to false)
            }
        }

        var plainFallback: String? = null
        try {
            val remaining = jobs.toMutableList()
            while (remaining.isNotEmpty()) {
                val (_, lrcPair) = select {
                    remaining.forEach { deferred -> deferred.onAwait { it } }
                }
                remaining.removeAll { it.isCompleted }
                val (lrc, hasWordTimings) = lrcPair
                if (lrc.isNotEmpty()) {
                    if (hasWordTimings) return Result.success(lrc)
                    else if (plainFallback == null) plainFallback = lrc
                }
            }
        } finally {
            scope.coroutineContext.cancelChildren()
        }

        plainFallback?.let { return Result.success(it) }
        error("No lyrics available from Paxsenix")
    }

    private fun scoreAndFilterResults(
        results: List<SearchResult>,
        title: String,
        artist: String,
        duration: Int,
    ): List<Pair<SearchResult, Double>> {
        val durationMs = duration * 1000
        val cleanupRegex = Regex("""\s*\(.*?\)|\s*\[.*?\]""")

        val cleanedTitle = title.replace(cleanupRegex, "").lowercase().trim()
        val cleanedArtist = cleanArtist(artist).lowercase()

        val targetIsMixed = title.contains("mixed", ignoreCase = true)
        val targetIsRemix = title.contains("remix", ignoreCase = true)

        return results.map { result ->
            var score = 0.0

            result.duration?.let { d ->
                val diff = abs(d - durationMs)
                when {
                    diff <= 2000 -> score += 100
                    diff <= 5000 -> score += 50
                    diff <= 10000 -> score += 10
                    else -> score -= 50
                }
            }

            val resultTitleCleaned = result.displayName.replace(cleanupRegex, "").lowercase().trim()
            when {
                resultTitleCleaned == cleanedTitle -> score += 80
                resultTitleCleaned.contains(cleanedTitle) || cleanedTitle.contains(resultTitleCleaned) -> score += 40
            }

            val resultIsMixed = result.displayName.contains("mixed", ignoreCase = true)
            val resultIsRemix = result.displayName.contains("remix", ignoreCase = true)
            if (resultIsMixed && !targetIsMixed) score -= 60
            if (resultIsRemix && !targetIsRemix) score -= 40

            val resultArtistLower = result.displayArtist.lowercase()
            when {
                resultArtistLower.contains(cleanedArtist) -> score += 50
                else -> {
                    val artistWords = cleanedArtist.split(Regex("\\s+")).filter { it.length > 2 }
                    if (artistWords.any { resultArtistLower.contains(it) }) score += 25
                }
            }

            result to score
        }.sortedByDescending { it.second }.filter { it.second > 0 }.take(10)
    }

    private suspend fun fetchLyricsForTrackWithType(id: String): Pair<String, Boolean> {
        val result = fetchLyricsForTrack(id)
        if (result.isSuccess) {
            val lrc = result.getOrNull()!!
            return lrc to (lrc.contains("<") && lrc.contains(">"))
        }
        return "" to false
    }

    private suspend fun fetchLyricsForTrack(id: String): Result<String> = runCatching {
        val response = httpClient.get("/apple-music/lyrics") {
            parameter("id", id)
        }.body<LyricsResponse>()

        if (!response.ttmlContent.isNullOrBlank()) {
            val lrc = convertTTMLToAppFormat(response.ttmlContent)
            if (lrc.isNotEmpty()) return@runCatching lrc
        }

        response.elrcMultiPerson?.takeIf { it.isNotBlank() }?.let { return@runCatching it }
        response.elrc?.takeIf { it.isNotBlank() }?.let { return@runCatching it }
        response.plain?.takeIf { it.isNotBlank() }?.let { return@runCatching it }

        if (response.content.isEmpty()) error("No lyrics found")

        if (response.type != "Syllable") {
            // "line" lyrics still carry a start time per line, dropping it turned synced lyrics into plain text
            val timed = response.content.any { it.timestamp > 0 }
            return@runCatching response.content.mapNotNull { line ->
                val lineText = line.text.joinedLineText()
                if (lineText.isBlank()) return@mapNotNull null
                if (!timed) return@mapNotNull lineText
                formatStamp(line.timestamp / 1000 / 60, line.timestamp / 1000 % 60, line.timestamp % 1000 / 10) + lineText
            }.joinToString("\n")
        }

        buildString {
            response.content.forEach { line ->
                val timeMs = line.timestamp
                val agent = when {
                    line.background -> "{bg}"
                    line.oppositeTurn -> "{agent:v2}"
                    else -> "{agent:v1}"
                }
                val lineText = line.text.joinedLineText()
                if (lineText.isNotBlank()) {
                    appendLine(
                        formatStamp(timeMs / 1000 / 60, timeMs / 1000 % 60, timeMs % 1000 / 10) + agent + lineText,
                    )
                    if (line.text.isNotEmpty()) {
                        val wordsData = line.text.joinToString("|") { word ->
                            "${word.text}:${word.timestamp.toDouble() / 1000}:${word.endtime.toDouble() / 1000}"
                        }
                        if (wordsData.isNotEmpty()) appendLine("<$wordsData>")
                    }
                }
            }
        }
    }

    private fun formatStamp(minutes: Long, seconds: Long, centiseconds: Long): String =
        "[${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}." +
            "${centiseconds.toString().padStart(2, '0')}]"

    suspend fun getAllLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        val cleanedTitle = cleanTitle(title)
        val cleanedArtist = cleanArtist(artist)

        val searchQueries = listOf("$cleanedTitle $cleanedArtist", cleanedTitle)
        var plainFallback: String? = null
        var scoredResults: List<Pair<SearchResult, Double>> = emptyList()

        for (query in searchQueries) {
            val results = search(query)
            if (results.isEmpty()) continue
            val filtered = scoreAndFilterResults(results, title, artist, duration)
            if (filtered.isNotEmpty()) {
                scoredResults = filtered
                break
            }
        }

        val candidates = scoredResults.take(3)
        val scope = CoroutineScope(Dispatchers.Default)
        val jobs = candidates.map { (result, _) ->
            scope.async {
                runCatching { fetchLyricsForTrackWithType(result.id) }.getOrDefault("" to false)
            }
        }
        try {
            val remaining = jobs.toMutableList()
            while (remaining.isNotEmpty()) {
                val (lrc, hasWordTimings) = select {
                    remaining.forEach { deferred -> deferred.onAwait { it } }
                }
                remaining.removeAll { it.isCompleted }
                if (lrc.isNotEmpty()) {
                    if (hasWordTimings) {
                        callback(lrc)
                        return
                    } else if (plainFallback == null) {
                        plainFallback = lrc
                    }
                }
            }
        } finally {
            scope.coroutineContext.cancelChildren()
        }

        plainFallback?.let(callback)
    }

    private fun convertTTMLToAppFormat(ttml: String): String = runCatching {
        TTMLParser.toLRC(TTMLParser.parseTTML(ttml))
    }.getOrDefault("")
}

/** a line's text from its timed pieces, syllable pieces are joined by the api's trailing space or the part flag */
private fun List<LyricText>.joinedLineText(): String {
    if (isEmpty()) return ""
    val spacedByApi = dropLast(1).any { it.text.lastOrNull()?.isWhitespace() == true }
    return buildString {
        this@joinedLineText.forEachIndexed { index, piece ->
            append(piece.text)
            val last = index == this@joinedLineText.lastIndex
            if (!spacedByApi && !last && !piece.part && piece.text.lastOrNull()?.isWhitespace() != true) {
                append(' ')
            }
        }
    }.replace(Regex("\\s+"), " ").trim()
}
