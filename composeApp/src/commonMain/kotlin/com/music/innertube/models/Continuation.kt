@file:OptIn(ExperimentalSerializationApi::class)

package com.music.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class Continuation(
    @JsonNames("nextContinuationData", "nextRadioContinuationData")
    val nextContinuationData: NextContinuationData? = null,
) {
    @Serializable
    data class NextContinuationData(
        val continuation: String? = null,
    )
}

fun List<Continuation>.getContinuation(): String? =
    firstNotNullOfOrNull { it.nextContinuationData?.continuation }

@Serializable
data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint? = null,
) {
    @Serializable
    data class ContinuationEndpoint(
        val continuationCommand: ContinuationCommand? = null,
    )
}
