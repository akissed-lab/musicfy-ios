package com.music.innertube.models

/**
 * Convenience aliases: upstream keeps these in `models.body` / `models.response`,
 * while the existing KMP slice keeps most Innertube models in `models`.
 * Both import paths therefore resolve to the same serializable classes.
 */
typealias SearchBody = com.music.innertube.models.body.SearchBody
typealias GetSearchSuggestionsBody = com.music.innertube.models.body.GetSearchSuggestionsBody
typealias SearchResponse = com.music.innertube.models.response.SearchResponse
typealias GetSearchSuggestionsResponse = com.music.innertube.models.response.GetSearchSuggestionsResponse
