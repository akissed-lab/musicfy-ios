package com.music.innertube.models.response

import com.music.innertube.models.Continuation
import com.music.innertube.models.MusicResponsiveListItemRenderer
import com.music.innertube.models.Tabs
import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
) {
    @Serializable
    data class Contents(
        val tabbedSearchResultsRenderer: Tabs? = null,
    )

    @Serializable
    data class ContinuationContents(
        val musicShelfContinuation: MusicShelfContinuation? = null,
    ) {
        @Serializable
        data class MusicShelfContinuation(
            val contents: List<Content> = emptyList(),
            val continuations: List<Continuation>? = null,
        ) {
            @Serializable
            data class Content(
                val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer = MusicResponsiveListItemRenderer(),
            )
        }
    }
}
