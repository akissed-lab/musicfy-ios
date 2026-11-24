package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicEditablePlaylistDetailHeaderRenderer(
    val header: Header = Header(),
    val editHeader: EditHeader? = null,
) {
    @Serializable
    data class Header(
        val musicDetailHeaderRenderer: MusicDetailHeaderRenderer? = null,
        val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer? = null,
    )

    @Serializable
    data class EditHeader(
        val musicPlaylistEditHeaderRenderer: MusicPlaylistEditHeaderRenderer? = null,
    )
}

@Serializable
data class MusicPlaylistEditHeaderRenderer(
    val editTitle: Runs? = null,
)
