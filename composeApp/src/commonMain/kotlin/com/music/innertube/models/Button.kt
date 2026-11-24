package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Button(
    val buttonRenderer: ButtonRenderer = ButtonRenderer(),
    /** Browse playlist headers put `menuRenderer` at the top level instead of inside `buttonRenderer`. */
    val menuRenderer: Menu.MenuRenderer? = null,
) {
    @Serializable
    data class ButtonRenderer(
        val text: Runs? = null,
        val navigationEndpoint: NavigationEndpoint? = null,
        val command: NavigationEndpoint? = null,
        val icon: Icon? = null,
        val menuRenderer: Menu.MenuRenderer? = null,
    )
}
