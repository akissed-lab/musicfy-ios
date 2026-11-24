package com.music.innertube.models.body

import com.music.innertube.models.Context
import kotlinx.serialization.Serializable

/** Body for `youtubei/v1/search`. Nullable fields are omitted from JSON by the client config. */
@Serializable
data class SearchBody(
    val context: Context,
    val query: String? = null,
    val params: String? = null,
)
