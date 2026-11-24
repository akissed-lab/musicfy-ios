package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class GridRenderer(
    val header: Header? = null,
    val items: List<Item> = emptyList(),
    val continuations: List<Continuation>? = null,
) {
    @Serializable
    data class Header(
        val gridHeaderRenderer: GridHeaderRenderer = GridHeaderRenderer(),
    ) {
        @Serializable
        data class GridHeaderRenderer(
            val title: Runs = Runs(),
        )
    }

    @Serializable
    data class Item(
        val musicNavigationButtonRenderer: MusicNavigationButtonRenderer? = null,
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer? = null,
    )
}
