package com.music.innertube.models.response

import com.music.innertube.models.Button
import com.music.innertube.models.Continuation
import com.music.innertube.models.GridRenderer
import com.music.innertube.models.Menu
import com.music.innertube.models.MusicDetailHeaderRenderer
import com.music.innertube.models.MusicEditablePlaylistDetailHeaderRenderer
import com.music.innertube.models.MusicShelfRenderer
import com.music.innertube.models.ResponseContext
import com.music.innertube.models.Runs
import com.music.innertube.models.SectionListRenderer
import com.music.innertube.models.SubscriptionButton
import com.music.innertube.models.Tabs
import com.music.innertube.models.ThumbnailRenderer
import com.music.innertube.models.TwoColumnBrowseResultsRenderer
import kotlinx.serialization.Serializable

/**
 * Full browse response. Deliberately separate from the tiny lyrics-only
 * `com.music.innertube.models.BrowseResponse` in `YouTubeLyricsModels.kt`.
 */
@Serializable
data class BrowseResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
    val onResponseReceivedActions: List<ResponseAction>? = null,
    val header: Header? = null,
    val microformat: Microformat? = null,
    val responseContext: ResponseContext? = null,
    val background: ThumbnailRenderer? = null,
) {
    @Serializable
    data class Contents(
        val singleColumnBrowseResultsRenderer: Tabs? = null,
        val sectionListRenderer: SectionListRenderer? = null,
        val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer? = null,
    )

    @Serializable
    data class ContinuationContents(
        val sectionListContinuation: SectionListContinuation? = null,
        val musicPlaylistShelfContinuation: MusicPlaylistShelfContinuation? = null,
        val gridContinuation: GridContinuation? = null,
        val musicShelfContinuation: MusicShelfRenderer? = null,
    ) {
        @Serializable
        data class SectionListContinuation(
            val contents: List<SectionListRenderer.Content> = emptyList(),
            val continuations: List<Continuation>? = null,
        )

        @Serializable
        data class MusicPlaylistShelfContinuation(
            val contents: List<MusicShelfRenderer.Content> = emptyList(),
            val continuations: List<Continuation>? = null,
        )

        @Serializable
        data class GridContinuation(
            val items: List<GridRenderer.Item> = emptyList(),
            val continuations: List<Continuation>? = null,
        )
    }

    @Serializable
    data class ResponseAction(
        val appendContinuationItemsAction: ContinuationItems? = null,
    ) {
        @Serializable
        data class ContinuationItems(
            val continuationItems: List<MusicShelfRenderer.Content>? = null,
        )
    }

    @Serializable
    data class Header(
        val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRenderer? = null,
        val musicDetailHeaderRenderer: MusicDetailHeaderRenderer? = null,
        val musicEditablePlaylistDetailHeaderRenderer: MusicEditablePlaylistDetailHeaderRenderer? = null,
        val musicVisualHeaderRenderer: MusicVisualHeaderRenderer? = null,
        val musicHeaderRenderer: MusicHeaderRenderer? = null,
    ) {
        @Serializable
        data class MusicImmersiveHeaderRenderer(
            val title: Runs = Runs(),
            val description: Runs? = null,
            val thumbnail: ThumbnailRenderer? = null,
            val playButton: Button? = null,
            val startRadioButton: Button? = null,
            val subscriptionButton: SubscriptionButton? = null,
            val menu: Menu = Menu(),
            val subscriptionButton2: SubscriptionButton2? = null,
            val monthlyListenerCount: Runs? = null,
        )

        @Serializable
        data class SubscriptionButton2(
            val subscribeButtonRenderer: SubscribeButtonRenderer = SubscribeButtonRenderer(),
        ) {
            @Serializable
            data class SubscribeButtonRenderer(
                val subscriberCountWithSubscribeText: Runs? = null,
            )
        }

        @Serializable
        data class MusicVisualHeaderRenderer(
            val title: Runs = Runs(),
            val foregroundThumbnail: ThumbnailRenderer? = null,
            val thumbnail: ThumbnailRenderer? = null,
        )

        @Serializable
        data class MusicHeaderRenderer(
            val buttons: List<Button> = emptyList(),
            val title: Runs? = null,
            val thumbnail: ThumbnailRenderer? = null,
            val subtitle: Runs? = null,
            val secondSubtitle: Runs? = null,
            val straplineTextOne: Runs? = null,
            val straplineThumbnail: ThumbnailRenderer? = null,
        )
    }

    @Serializable
    data class Microformat(
        val microformatDataRenderer: MicroformatDataRenderer? = null,
    ) {
        @Serializable
        data class MicroformatDataRenderer(
            val urlCanonical: String? = null,
        )
    }
}
