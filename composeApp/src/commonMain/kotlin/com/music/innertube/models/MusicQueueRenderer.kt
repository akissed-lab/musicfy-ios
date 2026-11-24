package com.music.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Only the `next` queue tab references this renderer in a browse response. WP-1 does not
 * consume it, so the opaque panel is kept as JSON rather than porting the full `next` tree.
 */
@Serializable
data class MusicQueueRenderer(
    val content: Content? = null,
    val header: Header? = null,
) {
    @Serializable
    data class Content(
        val playlistPanelRenderer: JsonElement? = null,
    )

    @Serializable
    data class Header(
        val musicQueueHeaderRenderer: MusicQueueHeaderRenderer? = null,
    ) {
        @Serializable
        data class MusicQueueHeaderRenderer(
            val title: Runs? = null,
            val subtitle: Runs? = null,
        )
    }
}
