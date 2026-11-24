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
import com.music.innertube.models.MUSIC_VIDEO_TYPE_ATV
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.Run
import com.music.innertube.models.SectionListRenderer
import com.music.innertube.models.YTItem
import com.music.innertube.models.body.SubscribeBody
import com.music.innertube.models.getContinuation
import com.music.innertube.models.getItems
import com.music.innertube.models.response.BrowseResponse as FullBrowseResponse
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
import com.music.innertube.pages.AlbumPage
import com.music.innertube.pages.ArtistItemsContinuationPage
import com.music.innertube.pages.ArtistItemsPage
import com.music.innertube.pages.ArtistPage
import com.music.innertube.pages.BrowseResult
import com.music.innertube.pages.NewReleaseAlbumPage
import com.music.innertube.pages.PlaylistContinuationPage
import com.music.innertube.pages.PlaylistPage
import com.music.innertube.pages.RelatedPage
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

    private suspend fun browsePage(
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): FullBrowseResponse = withRetry {
        httpClient.post("browse") {
            ytClient(YouTubeClient.WEB_REMIX)
            setBody(
                BrowseBody(
                    context = YouTubeClient.WEB_REMIX.toContext(visitorData),
                    browseId = browseId,
                    params = params,
                    continuation = continuation,
                ),
            )
        }.body<FullBrowseResponse>()
    }

    private fun mapBrowseRuns(runs: List<Run>?): List<Run>? =
        runs?.map { run -> Run(text = run.text, navigationEndpoint = run.navigationEndpoint) }

    private fun browseDescriptionRuns(response: FullBrowseResponse): List<Run>? {
        val runs = sequence {
            response.contents?.twoColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                tab?.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { content ->
                    content.musicDescriptionShelfRenderer?.description?.runs?.let { yield(it) }
                }
            }
            response.contents?.singleColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                tab.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { content ->
                    content.musicDescriptionShelfRenderer?.description?.runs?.let { yield(it) }
                }
            }
            response.contents?.sectionListRenderer?.contents?.forEach { content ->
                content.musicDescriptionShelfRenderer?.description?.runs?.let { yield(it) }
            }
            response.header?.musicDetailHeaderRenderer?.description?.runs?.let { yield(it) }
            response.header?.musicImmersiveHeaderRenderer?.description?.runs?.let { yield(it) }
            response.header?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicDetailHeaderRenderer
                ?.description?.runs?.let { yield(it) }
            response.header?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
                ?.description?.runs?.let { yield(it) }
            response.contents?.twoColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                tab?.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { content ->
                    content.musicResponsiveHeaderRenderer?.description?.runs?.let { yield(it) }
                }
            }
            response.contents?.singleColumnBrowseResultsRenderer?.tabs?.forEach { tab ->
                tab.tabRenderer?.content?.sectionListRenderer?.contents?.forEach { content ->
                    content.musicResponsiveHeaderRenderer?.description?.runs?.let { yield(it) }
                }
            }
        }.firstOrNull()
        return mapBrowseRuns(runs)
    }

    private fun FullBrowseResponse.albumCarousels(vararg keywords: String): List<AlbumItem> {
        val carousel = contents?.twoColumnBrowseResultsRenderer?.secondaryContents?.sectionListRenderer
            ?.contents?.firstNotNullOfOrNull { content ->
                val titleRuns = content.musicCarouselShelfRenderer?.header
                    ?.musicCarouselShelfBasicHeaderRenderer?.title?.runs.orEmpty()
                content.musicCarouselShelfRenderer?.takeIf {
                    keywords.any { keyword -> titleRuns.any { it.text.contains(keyword, ignoreCase = true) } }
                }
            }
        return carousel?.contents.orEmpty()
            .mapNotNull { it.musicTwoRowItemRenderer }
            .mapNotNull { NewReleaseAlbumPage.fromMusicTwoRowItemRenderer(it) }
    }

    private fun parseRelatedItems(contents: List<SectionListRenderer.Content>): List<YTItem> =
        contents.mapNotNull { content ->
            content.musicCarouselShelfRenderer?.let { renderer ->
                renderer.contents.mapNotNull { it.musicTwoRowItemRenderer }
                    .mapNotNull { RelatedPage.fromMusicTwoRowItemRenderer(it) }
            } ?: content.musicShelfRenderer?.let { renderer ->
                renderer.contents?.getItems()
                    ?.mapNotNull { SearchSummaryPage.fromMusicResponsiveListItemRenderer(it) }
            }
        }.flatten()

    suspend fun browse(browseId: String, params: String?): Result<BrowseResult> = runCatching {
        if (offline) error("Offline")
        val response = browsePage(browseId = browseId, params = params)
        BrowseResult(
            title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
            items = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents?.mapNotNull { content ->
                    when {
                        content.gridRenderer != null -> BrowseResult.Item(
                            title = content.gridRenderer.header?.gridHeaderRenderer?.title?.runs
                                ?.firstOrNull()?.text,
                            items = content.gridRenderer.items
                                .mapNotNull { it.musicTwoRowItemRenderer }
                                .mapNotNull { RelatedPage.fromMusicTwoRowItemRenderer(it) },
                        )

                        content.musicCarouselShelfRenderer != null -> BrowseResult.Item(
                            title = content.musicCarouselShelfRenderer.header
                                ?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text,
                            // genre pages list their "songs" shelf as rows, not cards
                            items = content.musicCarouselShelfRenderer.contents.mapNotNull { item ->
                                item.musicTwoRowItemRenderer?.let { RelatedPage.fromMusicTwoRowItemRenderer(it) }
                                    ?: item.musicResponsiveListItemRenderer?.let {
                                        RelatedPage.fromMusicResponsiveListItemRenderer(it)
                                    }
                            },
                        )

                        else -> null
                    }
                }.orEmpty(),
        )
    }

    suspend fun album(browseId: String, withSongs: Boolean = true): Result<AlbumPage> = runCatching {
        if (offline) error("Offline")
        val response = browsePage(browseId = browseId)
        val descriptionRuns = browseDescriptionRuns(response)
        val description = descriptionRuns?.joinToString(separator = "") { it.text }

        if (browseId.contains("FEmusic_library_privately_owned_release_detail")) {
            val detail = response.header?.musicDetailHeaderRenderer
                ?: error("No album detail header for $browseId")
            val playlistId = detail.menu.menuRenderer.topLevelButtons?.firstOrNull()
                ?.buttonRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.playlistId
                ?: error("No playlist id for album $browseId")
            val albumItem = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = detail.title?.runs?.firstOrNull()?.text ?: error("No album title for $browseId"),
                artists = detail.subtitle?.runs?.filter { it.navigationEndpoint != null }?.map {
                    Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
                },
                year = detail.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                thumbnail = detail.thumbnail.getThumbnailUrl() ?: error("No album thumbnail for $browseId"),
                description = description,
            )
            AlbumPage(
                album = albumItem,
                songs = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                    ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
                    ?.musicShelfRenderer?.contents?.getItems()
                    ?.mapNotNull { AlbumPage.getSong(it, albumItem) }.orEmpty().toMutableList(),
                otherVersions = emptyList(),
                description = description,
                descriptionRuns = descriptionRuns,
            )
        } else {
            val playlistId = AlbumPage.getPlaylistId(response) ?: error("No playlist id for album $browseId")
            val albumItem = AlbumItem(
                browseId = browseId,
                playlistId = playlistId,
                title = AlbumPage.getTitle(response) ?: error("No album title for $browseId"),
                artists = AlbumPage.getArtists(response),
                year = AlbumPage.getYear(response),
                thumbnail = AlbumPage.getThumbnail(response) ?: error("No album thumbnail for $browseId"),
                description = description,
            )
            AlbumPage(
                album = albumItem,
                songs = if (withSongs) albumSongs(playlistId, albumItem).getOrThrow() else emptyList(),
                otherVersions = response.albumCarousels("versions"),
                releasesForYou = response.albumCarousels("releases", "more from"),
                description = description,
                descriptionRuns = descriptionRuns,
            )
        }
    }

    suspend fun albumSongs(playlistId: String, album: AlbumItem? = null): Result<List<SongItem>> = runCatching {
        if (offline) error("Offline")
        var response = browsePage(browseId = "VL$playlistId")
        val shelf = response.contents?.twoColumnBrowseResultsRenderer?.secondaryContents
            ?.sectionListRenderer?.contents?.firstOrNull()?.musicPlaylistShelfRenderer
        val songs = shelf?.contents?.getItems()?.mapNotNull { AlbumPage.getSong(it, album) }
            .orEmpty().toMutableList()
        var continuation = shelf?.contents?.getContinuation()
        val seenContinuations = mutableSetOf<String>()
        var requestCount = 0
        val maxRequests = 50

        while (continuation != null && requestCount < maxRequests) {
            if (continuation in seenContinuations) break
            seenContinuations.add(continuation)
            requestCount++

            response = browsePage(continuation = continuation)
            songs += response.onResponseReceivedActions?.firstOrNull()
                ?.appendContinuationItemsAction?.continuationItems?.getItems()
                ?.mapNotNull { AlbumPage.getSong(it, album) }.orEmpty()
            continuation = response.continuationContents?.musicPlaylistShelfContinuation
                ?.continuations?.getContinuation()
        }
        songs
    }

    suspend fun artist(browseId: String): Result<ArtistPage> = runCatching {
        if (offline) error("Offline")
        val response = browsePage(browseId = browseId)
        val immersive = response.header?.musicImmersiveHeaderRenderer
        val visual = response.header?.musicVisualHeaderRenderer
        val descriptionRuns = mapBrowseRuns(
            response.contents?.sectionListRenderer?.contents
                ?.firstOrNull { it.musicDescriptionShelfRenderer != null }
                ?.musicDescriptionShelfRenderer?.description?.runs
                ?: immersive?.description?.runs,
        )
        val firstShelfItem = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
            ?.musicShelfRenderer?.contents?.firstOrNull()?.musicResponsiveListItemRenderer

        ArtistPage(
            artist = ArtistItem(
                id = browseId,
                title = immersive?.title?.runs?.firstOrNull()?.text
                    ?: visual?.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    ?: error("No artist header for $browseId"),
                thumbnail = immersive?.thumbnail?.getThumbnailUrl()
                    ?: visual?.foregroundThumbnail?.getThumbnailUrl()
                    ?: response.header?.musicDetailHeaderRenderer?.thumbnail?.getThumbnailUrl(),
                channelId = immersive?.subscriptionButton?.subscribeButtonRenderer?.channelId,
                playEndpoint = firstShelfItem?.overlay?.musicItemThumbnailOverlayRenderer?.content
                    ?.musicPlayButtonRenderer?.playNavigationEndpoint?.anyWatchEndpoint?.toShared(),
                shuffleEndpoint = immersive?.playButton?.buttonRenderer?.navigationEndpoint
                    ?.anyWatchEndpoint?.toShared()
                    ?: firstShelfItem?.navigationEndpoint?.watchPlaylistEndpoint?.toShared(),
                radioEndpoint = immersive?.startRadioButton?.buttonRenderer?.navigationEndpoint
                    ?.anyWatchEndpoint?.toShared(),
            ),
            sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionListRenderer?.contents
                ?.mapNotNull { ArtistPage.fromSectionListRendererContent(it) }.orEmpty(),
            description = descriptionRuns?.joinToString(separator = "") { it.text },
            subscriberCountText = immersive?.subscriptionButton2?.subscribeButtonRenderer
                ?.subscriberCountWithSubscribeText?.runs?.firstOrNull()?.text
                ?: immersive?.subscriptionButton?.subscribeButtonRenderer
                    ?.longSubscriberCountText?.runs?.firstOrNull()?.text
                ?: immersive?.subscriptionButton?.subscribeButtonRenderer
                    ?.shortSubscriberCountText?.runs?.firstOrNull()?.text,
            monthlyListenerCount = immersive?.monthlyListenerCount?.runs?.firstOrNull()?.text,
            descriptionRuns = descriptionRuns,
            subscribed = immersive?.subscriptionButton?.subscribeButtonRenderer?.subscribed,
        )
    }

    suspend fun artistItems(endpoint: BrowseEndpoint): Result<ArtistItemsPage> = runCatching {
        if (offline) error("Offline")
        val response = browsePage(browseId = endpoint.browseId, params = endpoint.params)
        val sectionContent = response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        val gridRenderer = sectionContent?.gridRenderer
        val carousel = sectionContent?.musicCarouselShelfRenderer
        val playlistShelf = sectionContent?.musicPlaylistShelfRenderer
        val musicShelf = sectionContent?.musicShelfRenderer

        when {
            gridRenderer != null -> ArtistItemsPage(
                title = gridRenderer.header?.gridHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = gridRenderer.items.mapNotNull { item ->
                    item.musicTwoRowItemRenderer?.let { ArtistItemsPage.fromMusicTwoRowItemRenderer(it) }
                },
                continuation = gridRenderer.continuations?.getContinuation(),
            )

            carousel != null -> ArtistItemsPage(
                title = carousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?.firstOrNull()?.text
                    .orEmpty(),
                items = carousel.contents.mapNotNull { content ->
                    content.musicTwoRowItemRenderer?.let { ArtistItemsPage.fromMusicTwoRowItemRenderer(it) }
                        ?: content.musicResponsiveListItemRenderer?.let {
                            ArtistItemsPage.fromMusicResponsiveListItemRenderer(it)
                        }
                },
                continuation = null,
            )

            musicShelf != null -> ArtistItemsPage(
                title = musicShelf.title?.runs?.firstOrNull()?.text
                    ?: response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = musicShelf.contents?.getItems()
                    ?.mapNotNull { ArtistItemsPage.fromMusicResponsiveListItemRenderer(it) }.orEmpty(),
                continuation = musicShelf.continuations?.getContinuation(),
            )

            else -> ArtistItemsPage(
                title = response.header?.musicHeaderRenderer?.title?.runs?.firstOrNull()?.text.orEmpty(),
                items = playlistShelf?.contents?.getItems()
                    ?.mapNotNull { ArtistItemsPage.fromMusicResponsiveListItemRenderer(it) }.orEmpty(),
                continuation = playlistShelf?.contents?.getContinuation(),
            )
        }
    }

    suspend fun artistItemsContinuation(continuation: String): Result<ArtistItemsContinuationPage> = runCatching {
        if (offline) error("Offline")
        val response = browsePage(continuation = continuation)

        when {
            response.continuationContents?.gridContinuation != null -> {
                val grid = response.continuationContents.gridContinuation
                val items = grid.items.mapNotNull {
                    it.musicTwoRowItemRenderer?.let { ArtistItemsPage.fromMusicTwoRowItemRenderer(it) }
                }
                ArtistItemsContinuationPage(
                    items = items,
                    continuation = if (items.isEmpty()) null else grid.continuations?.getContinuation(),
                )
            }

            response.continuationContents?.musicPlaylistShelfContinuation != null -> {
                val shelf = response.continuationContents.musicPlaylistShelfContinuation
                val items = shelf.contents.getItems()
                    .mapNotNull { ArtistItemsPage.fromMusicResponsiveListItemRenderer(it) }
                ArtistItemsContinuationPage(
                    items = items,
                    continuation = if (items.isEmpty()) null else shelf.continuations?.getContinuation(),
                )
            }

            else -> {
                val continuationItems = response.onResponseReceivedActions?.firstOrNull()
                    ?.appendContinuationItemsAction?.continuationItems
                val items = continuationItems?.getItems()
                    ?.mapNotNull { ArtistItemsPage.fromMusicResponsiveListItemRenderer(it) }.orEmpty()
                ArtistItemsContinuationPage(
                    items = items,
                    continuation = if (items.isEmpty()) null else continuationItems?.getContinuation(),
                )
            }
        }
    }

    suspend fun playlist(playlistId: String): Result<PlaylistPage> = runCatching {
        if (offline) error("Offline")
        val response = browsePage(browseId = "VL$playlistId")
        val base = response.contents?.twoColumnBrowseResultsRenderer?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        val header = base?.musicResponsiveHeaderRenderer
            ?: base?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
        val editable = base?.musicEditablePlaylistDetailHeaderRenderer != null
        val secondarySectionList = response.contents?.twoColumnBrowseResultsRenderer
            ?.secondaryContents?.sectionListRenderer

        var related = secondarySectionList?.contents?.let { parseRelatedItems(it.drop(1)) }
        if (related.isNullOrEmpty()) {
            secondarySectionList?.continuations?.getContinuation()?.let { token ->
                val continuationResponse = browsePage(continuation = token)
                val parsed = continuationResponse.continuationContents?.sectionListContinuation?.contents
                    ?.let { parseRelatedItems(it) }
                if (!parsed.isNullOrEmpty()) related = parsed
            }
        }

        val menuItems = header?.buttons?.lastOrNull()
            ?.let { it.menuRenderer ?: it.buttonRenderer.menuRenderer }
            ?.items
        PlaylistPage(
            playlist = PlaylistItem(
                id = playlistId,
                title = header?.title?.runs?.firstOrNull()?.text ?: error("No playlist header for $playlistId"),
                author = header?.straplineTextOne?.runs?.firstOrNull()?.let {
                    Artist(name = it.text, id = it.navigationEndpoint?.browseEndpoint?.browseId)
                },
                songCountText = header?.secondSubtitle?.runs?.firstOrNull()?.text,
                thumbnail = header?.thumbnail?.getThumbnailUrl(),
                playEndpoint = null,
                shuffleEndpoint = menuItems?.firstOrNull()
                    ?.menuNavigationItemRenderer?.navigationEndpoint?.watchPlaylistEndpoint?.toShared(),
                radioEndpoint = header?.buttons?.getOrNull(2)
                    ?.let { it.menuRenderer ?: it.buttonRenderer.menuRenderer }
                    ?.items
                    ?.firstNotNullOfOrNull { item ->
                        item.menuNavigationItemRenderer?.takeIf { it.icon?.iconType == "MIX" }
                            ?.navigationEndpoint?.watchPlaylistEndpoint?.toShared()
                    },
                isEditable = editable,
            ),
            songs = secondarySectionList?.contents?.firstOrNull()?.musicPlaylistShelfRenderer?.contents
                ?.getItems()?.mapNotNull { PlaylistPage.fromMusicResponsiveListItemRenderer(it) }.orEmpty(),
            songsContinuation = secondarySectionList?.contents?.firstOrNull()
                ?.musicPlaylistShelfRenderer?.contents?.getContinuation()
                ?: secondarySectionList?.contents?.firstOrNull()
                    ?.musicPlaylistShelfRenderer?.continuations?.getContinuation(),
            continuation = secondarySectionList?.continuations?.getContinuation(),
            related = related?.ifEmpty { null },
        )
    }

    suspend fun playlistContinuation(continuation: String): Result<PlaylistContinuationPage> = runCatching {
        if (offline) error("Offline")
        val response = browsePage(continuation = continuation)
        val mainContents = response.continuationContents?.sectionListContinuation?.contents
            ?.mapNotNull { it.musicPlaylistShelfRenderer?.contents }?.flatten() ?: emptyList()
        val shelfContents = response.continuationContents?.musicPlaylistShelfContinuation?.contents ?: emptyList()
        val appendedContents = response.onResponseReceivedActions?.firstOrNull()
            ?.appendContinuationItemsAction?.continuationItems.orEmpty()
        val allContents = mainContents + shelfContents + appendedContents

        val songs = allContents.mapNotNull { it.musicResponsiveListItemRenderer }
            .mapNotNull { PlaylistPage.fromMusicResponsiveListItemRenderer(it) }
        val nextContinuation = if (songs.isEmpty()) {
            null
        } else {
            response.continuationContents?.sectionListContinuation?.continuations?.getContinuation()
                ?: response.continuationContents?.musicPlaylistShelfContinuation?.continuations?.getContinuation()
                ?: response.continuationContents?.musicShelfContinuation?.continuations?.getContinuation()
                ?: response.onResponseReceivedActions?.firstOrNull()
                    ?.appendContinuationItemsAction?.continuationItems?.getContinuation()
        }
        PlaylistContinuationPage(songs = songs, continuation = nextContinuation)
    }

    suspend fun related(endpoint: BrowseEndpoint): Result<RelatedPage> = runCatching {
        if (offline) error("Offline")
        val response = browsePage(browseId = endpoint.browseId)
        val songs = mutableListOf<SongItem>()
        val albums = mutableListOf<AlbumItem>()
        val artists = mutableListOf<ArtistItem>()
        val playlists = mutableListOf<PlaylistItem>()

        fun addItem(item: YTItem, renderer: MusicResponsiveListItemRenderer?) {
            when (item) {
                is SongItem -> {
                    val isAudioTrack = renderer?.overlay?.musicItemThumbnailOverlayRenderer?.content
                        ?.musicPlayButtonRenderer?.playNavigationEndpoint?.musicVideoType == MUSIC_VIDEO_TYPE_ATV
                    if (isAudioTrack) songs.add(item)
                }
                is AlbumItem -> albums.add(item)
                is ArtistItem -> artists.add(item)
                is PlaylistItem -> playlists.add(item)
            }
        }

        response.contents?.sectionListRenderer?.contents?.forEach { sectionContent ->
            sectionContent.musicCarouselShelfRenderer?.contents?.forEach { content ->
                val item = content.musicResponsiveListItemRenderer?.let {
                    RelatedPage.fromMusicResponsiveListItemRenderer(it)
                } ?: content.musicTwoRowItemRenderer?.let { RelatedPage.fromMusicTwoRowItemRenderer(it) }
                if (item != null) addItem(item, content.musicResponsiveListItemRenderer)
            }
            sectionContent.musicShelfRenderer?.contents?.forEach { content ->
                val item = content.musicResponsiveListItemRenderer?.let {
                    RelatedPage.fromMusicResponsiveListItemRenderer(it)
                }
                if (item != null) addItem(item, content.musicResponsiveListItemRenderer)
            }
            sectionContent.itemSectionRenderer?.contents?.forEach { content ->
                val item = content.musicResponsiveListItemRenderer?.let {
                    RelatedPage.fromMusicResponsiveListItemRenderer(it)
                }
                if (item != null) addItem(item, content.musicResponsiveListItemRenderer)
            }
        }
        RelatedPage(songs, albums, artists, playlists)
    }

    suspend fun subscribeChannel(channelId: String, subscribe: Boolean) {
        if (offline) return
        runCatching {
            withRetry {
                httpClient.post(if (subscribe) "subscription/subscribe" else "subscription/unsubscribe") {
                    ytClient(YouTubeClient.WEB_REMIX)
                    setBody(
                        SubscribeBody(
                            context = YouTubeClient.WEB_REMIX.toContext(visitorData),
                            channelIds = listOf(channelId),
                        ),
                    )
                }
            }
        }
    }

    suspend fun getChannelId(browseId: String): String =
        artist(browseId).getOrNull()?.artist?.channelId.orEmpty()

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
