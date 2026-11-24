package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Menu(
    val menuRenderer: MenuRenderer = MenuRenderer(),
) {
    @Serializable
    data class MenuRenderer(
        val items: List<Item>? = null,
        val topLevelButtons: List<TopLevelButton>? = null,
    ) {
        @Serializable
        data class Item(
            val menuNavigationItemRenderer: MenuNavigationItemRenderer? = null,
            val menuServiceItemRenderer: MenuServiceItemRenderer? = null,
            val toggleMenuServiceItemRenderer: ToggleMenuServiceRenderer? = null,
        ) {
            @Serializable
            data class MenuNavigationItemRenderer(
                val text: Runs? = null,
                val icon: Icon? = null,
                val navigationEndpoint: NavigationEndpoint? = null,
            )

            @Serializable
            data class MenuServiceItemRenderer(
                val text: Runs? = null,
                val icon: Icon? = null,
                val serviceEndpoint: NavigationEndpoint? = null,
            )

            @Serializable
            data class ToggleMenuServiceRenderer(
                val defaultIcon: Icon? = null,
                val defaultServiceEndpoint: DefaultServiceEndpoint? = null,
                val toggledServiceEndpoint: ToggledServiceEndpoint? = null,
            ) {
                @Serializable
                data class DefaultServiceEndpoint(
                    val feedbackEndpoint: FeedbackEndpoint? = null,
                    val subscribeEndpoint: SubscribeEndpoint? = null,
                )

                @Serializable
                data class ToggledServiceEndpoint(
                    val feedbackEndpoint: FeedbackEndpoint? = null,
                )
            }
        }

        @Serializable
        data class TopLevelButton(
            val buttonRenderer: Button.ButtonRenderer? = null,
        )
    }
}
