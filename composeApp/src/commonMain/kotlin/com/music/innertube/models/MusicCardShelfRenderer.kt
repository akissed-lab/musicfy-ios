package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicCardShelfRenderer(
    val title: Runs = Runs(),
    val subtitle: Runs = Runs(),
    val thumbnail: ThumbnailRenderer = ThumbnailRenderer(),
    val header: Header? = null,
    val contents: List<Content>? = null,
    val buttons: List<Button> = emptyList(),
    val onTap: NavigationEndpoint = NavigationEndpoint(),
    val subtitleBadges: List<Badges>? = null,
) {
    @Serializable
    data class Header(
        val musicCardShelfHeaderBasicRenderer: MusicCardShelfHeaderBasicRenderer? = null,
    ) {
        @Serializable
        data class MusicCardShelfHeaderBasicRenderer(
            val title: Runs? = null,
        )
    }

    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    )
}
