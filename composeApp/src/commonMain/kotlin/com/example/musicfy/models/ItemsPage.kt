package com.example.musicfy.models

import com.music.innertube.models.YTItem

/** One filtered page of online search results, with its continuation token. */
data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
