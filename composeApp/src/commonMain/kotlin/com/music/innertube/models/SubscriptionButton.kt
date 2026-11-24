package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionButton(
    val subscribeButtonRenderer: SubscribeButtonRenderer = SubscribeButtonRenderer(),
) {
    @Serializable
    data class SubscribeButtonRenderer(
        val subscribed: Boolean? = null,
        val channelId: String? = null,
        val longSubscriberCountText: Runs? = null,
        val shortSubscriberCountText: Runs? = null,
    )
}
