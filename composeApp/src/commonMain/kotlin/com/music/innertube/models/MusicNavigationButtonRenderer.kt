package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicNavigationButtonRenderer(
    val buttonText: Runs = Runs(),
    val solid: Solid? = null,
    val iconStyle: IconStyle? = null,
    val clickCommand: NavigationEndpoint = NavigationEndpoint(),
) {
    @Serializable
    data class Solid(
        val leftStripeColor: Long? = null,
    )

    @Serializable
    data class IconStyle(
        val icon: Icon = Icon(),
    )
}
