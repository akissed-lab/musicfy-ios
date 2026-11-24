package com.music.innertube.models.body

import com.music.innertube.models.Context
import kotlinx.serialization.Serializable

/** Body for `youtubei/v1/get_search_suggestions`. */
@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
