package com.music.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ResponseContext(
    val visitorData: String? = null,
    val serviceTrackingParams: List<ServiceTrackingParam>? = null,
) {
    @Serializable
    data class ServiceTrackingParam(
        val params: List<Param> = emptyList(),
        val service: String? = null,
    ) {
        @Serializable
        data class Param(
            val key: String? = null,
            val value: String? = null,
        )
    }
}
