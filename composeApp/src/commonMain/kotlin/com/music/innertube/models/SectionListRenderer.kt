package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class SectionListRenderer(
    val contents: List<Content>? = null,
    val continuations: List<Continuation>? = null,
) {
    @Serializable
    data class Content(
        val musicShelfRenderer: MusicShelfRenderer? = null,
        val musicCardShelfRenderer: MusicCardShelfRenderer? = null,
        val itemSectionRenderer: ItemSectionRenderer? = null,
    )
}

@Serializable
data class ItemSectionRenderer(
    val contents: List<Content>? = null,
) {
    @Serializable
    data class Content(
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer? = null,
    )
}
