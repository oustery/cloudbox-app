package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FolderResponse(
    @Json(name = "body") val body: FolderBody? = null,
    @Json(name = "status") val status: Int? = null,
    @Json(name = "time") val time: Long? = null
)

@JsonClass(generateAdapter = true)
data class FolderBody(
    @Json(name = "list") val list: List<CloudItem>? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "count") val count: FolderCount? = null,
    @Json(name = "total") val total: FolderTotal? = null,
    @Json(name = "weblink") val weblink: String? = null,
    @Json(name = "kind") val kind: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "size") val size: Long? = 0L
)
