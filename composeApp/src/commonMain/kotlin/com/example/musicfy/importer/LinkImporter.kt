package com.example.musicfy.importer

import com.example.musicfy.network.createPlatformHttpClient
import com.example.musicfy.viewmodels.ImportSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class LinkSource(val label: String, val example: String) {
    SPOTIFY("Spotify", "https://open.spotify.com/playlist/…"),
    APPLE_MUSIC("Apple Music", "https://music.apple.com/us/playlist/…"),
    DEEZER("Deezer", "https://www.deezer.com/playlist/…"),
    YOUTUBE("YouTube Music", "https://music.youtube.com/playlist?list=…"),
}

class LinkImportException(message: String) : Exception(message)

/**
 * Reads a public playlist or album from a share link without logging in. Private playlists are
 * not reachable this way. No API keys, account tokens or WebView: only the public page HTML.
 */
object LinkImporter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client: HttpClient by lazy {
        createPlatformHttpClient().config {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            expectSuccess = false
        }
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    private val SPOTIFY_REGEX = Regex("(?:open\\.spotify\\.com/(?:intl-[a-zA-Z-]+/)?(?:embed/)?|spotify:)(playlist|album)[/:]([A-Za-z0-9]{22})")
    private val APPLE_REGEX = Regex("music\\.apple\\.com/[a-z]{2}/(playlist|album)/(?:[^/?#]+/)?(pl\\.[A-Za-z0-9-]+|\\d+)")
    private val DEEZER_REGEX = Regex("deezer\\.com/(?:[a-z]{2}/)?(playlist|album)/(\\d+)")
    private val YT_LIST_REGEX = Regex("[?&]list=([A-Za-z0-9_-]+)")
    private val YT_BROWSE_REGEX = Regex("music\\.youtube\\.com/(?:browse|playlist)/((?:VL|MPREb_)?[A-Za-z0-9_-]+)")
    private val SHORT_LINK_HOSTS = listOf("spotify.link", "spotify.app.link", "deezer.page.link", "link.deezer.com")

    /** Works out which service a pasted link belongs to, or null if it isn't one we read. */
    fun detect(link: String): LinkSource? {
        val text = link.trim()
        return when {
            text.contains("spotify") -> LinkSource.SPOTIFY
            text.contains("music.apple.com") -> LinkSource.APPLE_MUSIC
            text.contains("deezer") -> LinkSource.DEEZER
            text.contains("youtube.com") || text.contains("youtu.be") -> LinkSource.YOUTUBE
            else -> null
        }
    }

    suspend fun fetch(link: String): ParsedImport {
        val text = extractUrl(link.trim()) ?: throw LinkImportException("That doesn't look like a link.")
        val resolved = if (SHORT_LINK_HOSTS.any { text.contains(it) }) resolveShortLink(text) else text
        return when (detect(resolved) ?: detect(text)) {
            LinkSource.SPOTIFY -> fetchSpotify(resolved)
            LinkSource.APPLE_MUSIC -> fetchApple(resolved)
            // Deliberately not ported: this importer is the no-subscription Spotify/Apple path.
            LinkSource.DEEZER -> throw LinkImportException("Deezer links aren't supported in this build. Paste a Spotify or Apple Music link.")
            LinkSource.YOUTUBE -> throw LinkImportException("Paste a Spotify or Apple Music link. YouTube playlists are opened inside the app.")
            null -> throw LinkImportException("Paste a Spotify, Apple Music, Deezer or YouTube Music link.")
        }
    }

    /** Share sheets often paste "check out this playlist! https://…" — take just the url. */
    private fun extractUrl(text: String): String? {
        if (text.startsWith("spotify:")) return text
        return Regex("https?://\\S+").find(text)?.value?.trimEnd('.', ',', ')', '"', '\'')
    }

    /** Short links redirect to the real page; some go through an html page that holds the real url. */
    private suspend fun resolveShortLink(url: String): String {
        val response = get(url)
        val finalUrl = response.call.request.url.toString()
        if (listOf(SPOTIFY_REGEX, DEEZER_REGEX, YT_LIST_REGEX).any { it.containsMatchIn(finalUrl) }) return finalUrl
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        SPOTIFY_REGEX.find(body)?.let { return "https://open.spotify.com/${it.groupValues[1]}/${it.groupValues[2]}" }
        DEEZER_REGEX.find(body)?.let { return "https://www.deezer.com/${it.groupValues[1]}/${it.groupValues[2]}" }
        return finalUrl
    }

    private suspend fun get(url: String): HttpResponse = try {
        client.get(url) {
            header("User-Agent", USER_AGENT)
            header("Accept-Language", "en-US,en;q=0.9")
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        throw LinkImportException("Couldn't reach the server — check your connection.")
    }

    private suspend fun getOk(url: String, what: String): String {
        val response = get(url)
        when (response.status.value) {
            in 200..299 -> return response.bodyAsText()
            404 -> throw LinkImportException("$what wasn't found. Is it public?")
            429 -> throw LinkImportException("$what is rate-limiting us. Try again in a minute.")
            else -> throw LinkImportException("$what answered with an error (${response.status.value}).")
        }
    }

    // Spotify: the embed player page carries the track list for logged-out visitors.

    private suspend fun fetchSpotify(url: String): ParsedImport {
        val match = SPOTIFY_REGEX.find(url)
            ?: throw LinkImportException("Paste a Spotify playlist or album link.")
        val (type, id) = match.destructured
        return parseSpotifyEmbed(getOk("https://open.spotify.com/embed/$type/$id", "Spotify"), type)
            .from(PlaylistOrigin(remoteId = id, remoteUrl = "https://open.spotify.com/$type/$id"))
    }

    internal fun parseSpotifyEmbed(html: String, type: String): ParsedImport {
        val data = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: throw LinkImportException("Spotify changed its page — use TuneMyMusic for now.")
        val entity = json.parseToJsonElement(data)
            .path("props", "pageProps", "state", "data", "entity") as? JsonObject
            ?: throw LinkImportException("That Spotify $type is private or doesn't exist.")

        val name = entity.string("name") ?: entity.string("title") ?: "Spotify $type"
        val albumName = if (type == "album") name else null
        val tracks = (entity["trackList"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val title = obj.string("title") ?: return@mapNotNull null
            ImportedTrack(
                title = title,
                artist = obj.string("subtitle").orEmpty().replace('\u00A0', ' '),
                album = albumName,
                durationMs = obj.long("duration"),
                sourceId = obj.string("uri")?.takeIf { it.startsWith("spotify:track:") },
            )
        }
        if (tracks.isEmpty()) throw LinkImportException("That Spotify $type has no songs we can read.")

        val warnings = buildList {
            if (type == "playlist" && tracks.size >= SPOTIFY_EMBED_LIMIT) {
                add("Spotify only shares the first $SPOTIFY_EMBED_LIMIT songs of a playlist through links. For the whole playlist, use the TuneMyMusic CSV option.")
            }
        }
        val cover = (entity.path("coverArt", "sources") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .maxByOrNull { it.long("width") ?: 0 }
            ?.string("url")
        return ParsedImport(
            likedSongs = emptyList(),
            playlists = mapOf(name to tracks),
            warnings = warnings,
            provider = ImportSource.SPOTIFY,
            playlistCovers = coverFor(name, cover),
        )
    }

    internal const val SPOTIFY_EMBED_LIMIT = 100

    // Apple Music: the web page embeds its own data as serialized-server-data.

    private suspend fun fetchApple(url: String): ParsedImport {
        val match = APPLE_REGEX.find(url)
            ?: throw LinkImportException("Paste an Apple Music playlist or album link.")
        val type = match.groupValues[1]
        val page = url.substringBefore('#')
        return parseApplePage(getOk(page, "Apple Music"), type)
            .from(PlaylistOrigin(remoteId = match.groupValues[2], remoteUrl = page))
    }

    internal fun parseApplePage(html: String, type: String): ParsedImport {
        val data = Regex("<script[^>]*id=\"serialized-server-data\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1)
            ?: throw LinkImportException("Apple Music changed its page — use TuneMyMusic for now.")
        val root = json.parseToJsonElement(data)
        val page = (root.path("data") as? JsonArray)?.firstOrNull()?.path("data")
            ?: (root as? JsonArray)?.firstOrNull()?.path("data")
            ?: throw LinkImportException("That Apple Music $type is private or doesn't exist.")
        val sections = (page.path("sections") as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

        val header = sections.firstOrNull { it.string("itemKind") == "containerDetailHeaderLockup" }
            ?.let { (it["items"] as? JsonArray)?.firstOrNull() as? JsonObject }
        val name = header?.string("title") ?: "Apple Music $type"
        val albumArtist = header?.let { (it["subtitleLinks"] as? JsonArray)?.firstOrNull()?.path("title") as? JsonPrimitive }?.contentOrNull
        val declaredCount = header?.long("trackCount")?.toInt()

        val tracks = sections.filter { it.string("itemKind") == "trackLockup" }
            .flatMap { (it["items"] as? JsonArray).orEmpty() }
            .mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val title = obj.string("title") ?: return@mapNotNull null
                val artists = (obj["subtitleLinks"] as? JsonArray).orEmpty()
                    .mapNotNull { (it.path("title") as? JsonPrimitive)?.contentOrNull }
                ImportedTrack(
                    title = title,
                    artist = obj.string("artistName") ?: artists.joinToString(", ").ifEmpty { albumArtist.orEmpty() },
                    album = if (type == "album") name
                    else (obj["tertiaryLinks"] as? JsonArray)?.firstOrNull()?.let { (it.path("title") as? JsonPrimitive)?.contentOrNull },
                    durationMs = obj.long("duration"),
                )
            }
        if (tracks.isEmpty()) throw LinkImportException("That Apple Music $type has no songs we can read.")

        val warnings = buildList {
            if (declaredCount != null && declaredCount > tracks.size) {
                add("Apple Music's page only listed ${tracks.size} of $declaredCount songs. For the whole playlist, use the TuneMyMusic CSV option.")
            }
        }
        // The cover is a template: {w}x{h}, with {c}/{f} for crop and format.
        val cover = (header?.path("artwork", "dictionary", "url") as? JsonPrimitive)?.contentOrNull
            ?.replace("{w}", "600")?.replace("{h}", "600")?.replace("{c}", "bb")?.replace("{f}", "jpg")
        return ParsedImport(
            likedSongs = emptyList(),
            playlists = mapOf(name to tracks),
            warnings = warnings,
            provider = ImportSource.APPLE_MUSIC,
            playlistCovers = coverFor(name, cover),
        )
    }

    private fun coverFor(name: String, cover: String?): Map<String, String> =
        if (cover.isNullOrBlank()) emptyMap() else mapOf(name to cover)

    /** A link reads one playlist, so the origin is where that one lives. */
    private fun ParsedImport.from(origin: PlaylistOrigin) = copy(playlistOrigins = playlists.keys.associateWith { origin })
}
