@file:OptIn(ExperimentalSerializationApi::class)

package com.music.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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
        @JsonNames("musicImmersiveCarouselShelfRenderer")
        val musicCarouselShelfRenderer: MusicCarouselShelfRenderer? = null,
        val musicPlaylistShelfRenderer: MusicPlaylistShelfRenderer? = null,
        val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer? = null,
        val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer? = null,
        val musicEditablePlaylistDetailHeaderRenderer: MusicEditablePlaylistDetailHeaderRenderer? = null,
        val gridRenderer: GridRenderer? = null,
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
