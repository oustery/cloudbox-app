package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs of the Yandex.Disk public API (https://cloud-api.yandex.net).
 *
 * Public resources do NOT require an OAuth token: the `public_key` query
 * parameter carries the public link (or its normalized URL) and the API
 * answers with the resource tree, preview URLs and signed download hrefs.
 */

/** Response of GET /v1/disk/public/resources — a resource with optional children. */
@JsonClass(generateAdapter = true)
data class YandexPublicResource(
    @Json(name = "public_key") val publicKey: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "type") val type: String? = null, // "file" | "dir"
    @Json(name = "path") val path: String? = null,
    @Json(name = "mime_type") val mimeType: String? = null,
    @Json(name = "media_type") val mediaType: String? = null, // image | video | audio | ...
    @Json(name = "file") val file: String? = null, // time-limited direct download URL (files only)
    @Json(name = "preview") val preview: String? = null, // time-limited preview URL
    @Json(name = "size") val size: Long = 0L,
    @Json(name = "created") val created: String? = null, // ISO-8601
    @Json(name = "modified") val modified: String? = null, // ISO-8601
    @Json(name = "revision") val revision: Long = 0L,
    @Json(name = "_embedded") val embedded: YandexEmbedded? = null
)

/** Children listing of a public directory. */
@JsonClass(generateAdapter = true)
data class YandexEmbedded(
    @Json(name = "items") val items: List<YandexPublicResource> = emptyList(),
    @Json(name = "sort") val sort: String? = null,
    @Json(name = "total") val total: Int = 0,
    @Json(name = "offset") val offset: Int = 0,
    @Json(name = "limit") val limit: Int = 0,
    @Json(name = "path") val path: String? = null
)

/** Response of GET /v1/disk/public/resources/download — a signed download href. */
@JsonClass(generateAdapter = true)
data class YandexDownloadResponse(
    @Json(name = "href") val href: String = "",
    @Json(name = "method") val method: String? = null,
    @Json(name = "templated") val templated: Boolean = false
)
