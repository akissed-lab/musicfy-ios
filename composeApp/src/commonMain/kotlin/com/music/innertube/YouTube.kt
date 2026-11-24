@file:OptIn(ExperimentalSerializationApi::class, ExperimentalEncodingApi::class)

package com.music.innertube

import com.example.musicfy.network.createPlatformHttpClient
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.BrowseEndpoint
import com.music.innertube.models.BrowseBody
import com.music.innertube.models.BrowseResponse
import com.music.innertube.models.GetTranscriptBody
import com.music.innertube.models.GetTranscriptResponse
import com.music.innertube.models.NextBody
import com.music.innertube.models.NextResponse
import com.music.innertube.models.NextResult
import com.music.innertube.models.PlayerBody
import com.music.innertube.models.PlayerResponse
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SearchSuggestions
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.API_URL_YOUTUBE_MUSIC
import com.music.innertube.models.YouTubeClient.Companion.ORIGIN_YOUTUBE_MUSIC
import com.music.innertube.models.YouTubeClient.Companion.REFERER_YOUTUBE_MUSIC
import com.music.innertube.models.body.GetSearchSuggestionsBody
import com.music.innertube.models.body.SearchBody
import com.music.innertube.models.response.GetSearchSuggestionsResponse
import com.music.innertube.models.response.SearchResponse
import com.music.innertube.pages.SearchPage
import com.music.innertube.pages.SearchResult
import com.music.innertube.pages.SearchSuggestionPage
import com.music.innertube.pages.SearchSummary
import com.music.innertube.pages.SearchSummaryPage
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * KMP slice of upstream Innertube: search transport plus the lyrics endpoints.
 *
 * Android's OkHttp/proxy/PoToken machinery is replaced by the Darwin engine.
 */
object YouTube {
    /** Set by `OfflineMode` so a pending request stops going online. */
    var offline: Boolean = false

    /** Sent as `X-Goog-Visitor-Id` and in the Innertube client context when set. */
    var visitorData: String? = null

    private const val DEFAULT_TOP_RESULT = "Top result"
    private const val DEFAULT_OTHER_RESULTS = "Other"

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
            url(API_URL_YOUTUBE_MUSIC)
            header("Accept", "application/json")
            header("Accept-Language", "en-US,en;q=0.9")
            header("Cache-Control", "no-cache")
        }
    }

    private val CAPTION_CLIENTS = listOf(
        YouTubeClient.ANDROID_VR_NO_AUTH,
        YouTubeClient.IOS,
        YouTubeClient.WEB_REMIX,
    )

    private fun HttpRequestBuilder.ytClient(client: YouTubeClient) {
        contentType(ContentType.Application.Json)
        header("X-Goog-Api-Format-Version", "1")
        header("X-YouTube-Client-Name", client.clientId)
        header("X-YouTube-Client-Version", client.clientVersion)
        header("X-Origin", ORIGIN_YOUTUBE_MUSIC)
        header("Referer", REFERER_YOUTUBE_MUSIC)
        visitorData?.let { header("X-Goog-Visitor-Id", it) }
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

    suspend fun searchSuggestions(query: String): Result<SearchSuggestions> = runCatching {
        if (offline) error("Offline")
        val response = withRetry {
            httpClient.post("music/get_search_suggestions") {
                ytClient(YouTubeClient.WEB_REMIX)
                setBody(
                    GetSearchSuggestionsBody(
                        context = YouTubeClient.WEB_REMIX.toContext(visitorData),
                        input = query,
                    ),
                )
            }.body<GetSearchSuggestionsResponse>()
        }
        SearchSuggestions(
            queries = response.contents?.getOrNull(0)?.searchSuggestionsSectionRenderer?.contents
                ?.mapNotNull { content ->
                    content.searchSuggestionRenderer?.suggestion?.runs?.joinToString(separator = "") { it.text }
                }
                .orEmpty(),
            recommendedItems = response.contents?.getOrNull(1)?.searchSuggestionsSectionRenderer?.contents
                ?.mapNotNull { content ->
                    content.musicResponsiveListItemRenderer?.let { renderer ->
                        SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
                    }
                }
                .orEmpty(),
        )
    }

    suspend fun searchSummary(query: String): Result<SearchSummaryPage> = runCatching {
        if (offline) error("Offline")
        val response = withRetry {
            httpClient.post("search") {
                ytClient(YouTubeClient.WEB_REMIX)
                setBody(
                    SearchBody(
                        context = YouTubeClient.WEB_REMIX.toContext(visitorData),
                        query = query,
                        params = null,
                    ),
                )
            }.body<SearchResponse>()
        }
        val contents = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents.orEmpty()

        val shelfSummaries = contents.mapNotNull { content ->
            val card = content.musicCardShelfRenderer
            val shelf = content.musicShelfRenderer
            when {
                card != null -> {
                    val top = SearchSummaryPage.fromMusicCardShelfRenderer(card)
                    val cardArtist = card.onTap.browseEndpoint
                        ?.takeIf { it.isArtistEndpoint }
                        ?.let { endpoint ->
                            card.title.runs?.firstOrNull()?.text?.let { name ->
                                Artist(name = name, id = endpoint.browseId)
                            }
                        }
                    val items = listOfNotNull(top)
                        .plus(
                            card.contents
                                ?.mapNotNull { it.musicResponsiveListItemRenderer }
                                ?.mapNotNull { renderer ->
                                    SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer, cardArtist)
                                }
                                .orEmpty(),
                        )
                        .distinctBy { it.id }
                    if (items.isEmpty()) null else SearchSummary(
                        title = card.header?.musicCardShelfHeaderBasicRenderer?.title?.runs?.firstOrNull()?.text
                            ?: DEFAULT_TOP_RESULT,
                        items = items,
                    )
                }
                shelf != null -> {
                    val items = shelf.contents.orEmpty()
                        .mapNotNull { it.musicResponsiveListItemRenderer }
                        .mapNotNull { renderer ->
                            SearchSummaryPage.fromMusicResponsiveListItemRenderer(renderer)
                        }
                        .distinctBy { it.id }
                    if (items.isEmpty()) null else SearchSummary(
                        title = shelf.title?.runs?.firstOrNull()?.text ?: DEFAULT_OTHER_RESULTS,
                        items = items,
                    )
                }
                else -> null
            }
        }

        val flatItems = contents
            .mapNotNull { it.itemSectionRenderer }
            .flatMap { it.contents.orEmpty() }
            .mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { renderer ->
                SearchPage.toYTItem(renderer)
                    ?: SearchSuggestionPage.fromMusicResponsiveListItemRenderer(renderer)
            }

        val groupedSummaries = mutableListOf<SearchSummary>()
        val flatSongs = flatItems.filterIsInstance<SongItem>().filter { !it.isVideoSong }
        if (flatSongs.isNotEmpty()) {
            groupedSummaries.add(SearchSummary(title = "Songs", items = flatSongs))
        }
        val flatVideos = flatItems.filterIsInstance<SongItem>().filter { it.isVideoSong }
        if (flatVideos.isNotEmpty()) {
            groupedSummaries.add(SearchSummary(title = "Videos", items = flatVideos))
        }
        val flatAlbums = flatItems.filterIsInstance<AlbumItem>()
        if (flatAlbums.isNotEmpty()) {
            groupedSummaries.add(SearchSummary(title = "Albums", items = flatAlbums))
        }
        val flatArtists = flatItems.filterIsInstance<ArtistItem>()
        if (flatArtists.isNotEmpty()) {
            groupedSummaries.add(SearchSummary(title = "Artists", items = flatArtists))
        }
        val flatPlaylists = flatItems.filterIsInstance<PlaylistItem>()
        if (flatPlaylists.isNotEmpty()) {
            groupedSummaries.add(SearchSummary(title = "Playlists", items = flatPlaylists))
        }

        SearchSummaryPage(summaries = shelfSummaries + groupedSummaries)
    }

    suspend fun search(query: String, filter: com.music.innertube.SearchFilter): Result<SearchResult> = runCatching {
        if (offline) error("Offline")
        val response = withRetry {
            httpClient.post("search") {
                ytClient(YouTubeClient.WEB_REMIX)
                setBody(
                    SearchBody(
                        context = YouTubeClient.WEB_REMIX.toContext(visitorData),
                        query = query,
                        params = filter.value,
                    ),
                )
            }.body<SearchResponse>()
        }
        val musicShelfRenderer = response.contents?.tabbedSearchResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents
            ?.mapNotNull { it.musicShelfRenderer }
            ?.firstOrNull()
        SearchResult(
            items = musicShelfRenderer?.contents
                ?.mapNotNull { it.musicResponsiveListItemRenderer }
                ?.mapNotNull { SearchPage.toYTItem(it) }
                .orEmpty(),
            continuation = musicShelfRenderer?.continuations
                ?.firstOrNull()?.nextContinuationData?.continuation,
        )
    }

    suspend fun searchContinuation(continuation: String): Result<SearchResult> = runCatching {
        if (offline) error("Offline")
        val response = withRetry {
            httpClient.post("search") {
                ytClient(YouTubeClient.WEB_REMIX)
                setBody(
                    SearchBody(
                        context = YouTubeClient.WEB_REMIX.toContext(visitorData),
                        query = null,
                        params = null,
                    ),
                )
                parameter("continuation", continuation)
                parameter("ctoken", continuation)
            }.body<SearchResponse>()
        }
        val shelf = response.continuationContents?.musicShelfContinuation
        val items = shelf?.contents
            ?.mapNotNull { it.musicResponsiveListItemRenderer }
            ?.mapNotNull { SearchPage.toYTItem(it) }
            .orEmpty()
        SearchResult(
            items = items,
            continuation = if (items.isEmpty()) {
                null
            } else {
                shelf?.continuations?.firstOrNull()?.nextContinuationData?.continuation
            },
        )
    }

    /** Compatibility with call sites written against upstream's nested filter. */
    object SearchFilter {
        val FILTER_SONG = com.music.innertube.SearchFilter.FILTER_SONG
        val FILTER_VIDEO = com.music.innertube.SearchFilter.FILTER_VIDEO
        val FILTER_ALBUM = com.music.innertube.SearchFilter.FILTER_ALBUM
        val FILTER_ARTIST = com.music.innertube.SearchFilter.FILTER_ARTIST
        val FILTER_FEATURED_PLAYLIST = com.music.innertube.SearchFilter.FILTER_FEATURED_PLAYLIST
        val FILTER_COMMUNITY_PLAYLIST = com.music.innertube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
    }

    suspend fun transcript(videoId: String): Result<String> = runCatching {
        if (offline) error("Offline")
        val response = withRetry {
            httpClient.post("https://music.youtube.com/youtubei/v1/get_transcript") {
                contentType(ContentType.Application.Json)
                setBody(
                    GetTranscriptBody(
                        context = YouTubeClient.WEB.toContext(),
                        params = Base64.Default.encode("\n${11.toChar()}$videoId".encodeToByteArray()),
                    ),
                )
            }.body<GetTranscriptResponse>()
        }
        val groups = response.actions?.firstOrNull()
            ?.updateEngagementPanelAction?.content?.transcriptRenderer?.body
            ?.transcriptBodyRenderer?.cueGroups
            ?: error("No transcript for $videoId")
        groups.joinToString("\n") { group ->
            val cue = group.transcriptCueGroupRenderer?.cues?.firstOrNull()?.transcriptCueRenderer
                ?: return@joinToString ""
            val text = cue.cue?.simpleText.orEmpty().trim('♪').trim(' ')
            "${stamp(cue.startOffsetMs)}$text"
        }
    }

    /** the video's captions as line-synced lyrics: auto-generated or an uploaded track in the same language */
    suspend fun captions(videoId: String): Result<String> = runCatching {
        if (offline) error("Offline")
        CAPTION_CLIENTS.firstNotNullOfOrNull { client ->
            runCatching { captionsAs(videoId, client) }.getOrNull()?.takeIf { it.isNotBlank() }
        } ?: throw IllegalStateException("No captions for $videoId")
    }

    private suspend fun captionsAs(videoId: String, client: YouTubeClient): String? {
        val response = withRetry {
            httpClient.post("player") {
                ytClient(client)
                setBody(PlayerBody(context = client.toContext(), videoId = videoId))
            }.body<PlayerResponse>()
        }
        val tracks = response.captions?.playerCaptionsTracklistRenderer?.captionTracks.orEmpty()
        val track = pickCaptionTrack(tracks) ?: return null
        val timedText = withRetry {
            httpClient.get(track.baseUrl) {
                url.parameters["fmt"] = "json3"
                userAgent(client.userAgent)
            }.bodyAsText()
        }
        return timedTextToLrc(timedText)
    }

    suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult> = runCatching {
        if (offline) error("Offline")
        val response = withRetry {
            httpClient.post("next") {
                ytClient(YouTubeClient.WEB_REMIX)
                setBody(
                    NextBody(
                        context = YouTubeClient.WEB_REMIX.toContext(),
                        videoId = endpoint.videoId,
                        continuation = continuation,
                    ),
                )
            }.body<NextResponse>()
        }
        val tabs = response.contents?.singleColumnMusicWatchNextResultsRenderer
            ?.tabbedRenderer?.watchNextTabbedResultsRenderer?.tabs.orEmpty()
        NextResult(lyricsEndpoint = tabs.getOrNull(1)?.tabRenderer?.endpoint?.browseEndpoint)
    }

    suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?> = runCatching {
        if (offline) error("Offline")
        val response = withRetry {
            httpClient.post("browse") {
                ytClient(YouTubeClient.WEB_REMIX)
                setBody(
                    BrowseBody(
                        context = YouTubeClient.WEB_REMIX.toContext(),
                        browseId = endpoint.browseId,
                        params = endpoint.params,
                    ),
                )
            }.body<BrowseResponse>()
        }
        response.contents?.sectionListRenderer?.contents
            ?.firstOrNull { it.musicDescriptionShelfRenderer != null }
            ?.musicDescriptionShelfRenderer?.description?.runs
            ?.joinToString(separator = "") { it.text }
    }

    private fun stamp(ms: Long): String =
        "[${(ms / 60_000).toString().padStart(2, '0')}:" +
            "${(ms / 1000 % 60).toString().padStart(2, '0')}." +
            "${(ms % 1000).toString().padStart(3, '0')}]"
}
