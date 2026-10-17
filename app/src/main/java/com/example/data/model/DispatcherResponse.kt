package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DispatcherResponse(
    @Json(name = "body") val body: DispatcherBody? = null,
    @Json(name = "status") val status: Int? = null
)

@JsonClass(generateAdapter = true)
data class DispatcherBody(
    @Json(name = "weblink_get") val weblinkGet: List<ShardNode>? = null,
    @Json(name = "weblink_thumbnails") val weblinkThumbnails: List<ShardNode>? = null,
    @Json(name = "weblink_view") val weblinkView: List<ShardNode>? = null,
    @Json(name = "video") val video: List<ShardNode>? = null
)

@JsonClass(generateAdapter = true)
data class ShardNode(
    @Json(name = "count") val count: String? = null,
    @Json(name = "url") val url: String = ""
)
