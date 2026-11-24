package com.music.innertube.models

import kotlinx.serialization.Serializable

/** Additive, browse-slice counterpart of upstream `SubscribeEndpoint`. */
@Serializable
data class SubscribeEndpoint(
    val channelIds: List<String> = emptyList(),
)
