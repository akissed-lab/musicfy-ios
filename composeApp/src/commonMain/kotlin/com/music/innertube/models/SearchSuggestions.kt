package com.music.innertube.models

/** KMP port of upstream `SearchSuggestions`: query completions plus recommended YT items. */
data class SearchSuggestions(
    val queries: List<String>,
    val recommendedItems: List<YTItem>,
)
