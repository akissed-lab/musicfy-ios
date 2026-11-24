package com.music.innertube.pages

import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts

data class BrowseResult(
    val title: String?,
    val items: List<Item>,
) {
    data class Item(
        val title: String?,
        val items: List<YTItem>,
    )

    fun filterExplicit(enabled: Boolean = true) =
        if (enabled) {
            copy(
                items = items.mapNotNull { item ->
                    item.copy(
                        items = item.items.filterExplicit().ifEmpty { return@mapNotNull null },
                    )
                },
            )
        } else {
            this
        }

    fun filterVideoSongs(disableVideos: Boolean = false) =
        if (disableVideos) {
            copy(
                items = items.mapNotNull { item ->
                    item.copy(
                        items = item.items.filterVideoSongs(true).ifEmpty { return@mapNotNull null },
                    )
                },
            )
        } else {
            this
        }

    fun filterYoutubeShorts(enabled: Boolean = false) =
        if (enabled) {
            copy(
                items = items.mapNotNull { item ->
                    item.copy(
                        items = item.items.filterYoutubeShorts(true).ifEmpty { return@mapNotNull null },
                    )
                },
            )
        } else {
            this
        }
}
