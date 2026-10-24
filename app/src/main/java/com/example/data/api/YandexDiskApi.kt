package com.example.data.api

import com.example.data.model.YandexDownloadResponse
import com.example.data.model.YandexPublicResource
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Yandex.Disk public API. Base URL: https://cloud-api.yandex.net/
 *
 * All endpoints are anonymous — public resources are accessed by [publicKey],
 * which accepts the public link itself (https://disk.yandex.ru/d/...).
 */
interface YandexDiskApi {

    /**
     * Metadata of a public resource.
     *
     * @param publicKey the public link (normalized URL)
     * @param path      path INSIDE the public folder ("/sub/dir"), null for the root
     * @param limit     page size for directory listings (server max = 1000)
     * @param offset    pagination offset
     * @param previewSize named preview size (S/M/L/XL/XXL/XXXL) embedded into item.preview
     */
    @GET("v1/disk/public/resources")
    suspend fun getPublicResource(
        @Query("public_key") publicKey: String,
        @Query("path") path: String? = null,
        @Query("limit") limit: Int = 200,
        @Query("offset") offset: Int = 0,
        @Query("preview_size") previewSize: String = "XL"
    ): YandexPublicResource

    /**
     * Signed, time-limited download URL of a public resource.
     * The href answers 302 to the storage shard and supports byte ranges.
     */
    @GET("v1/disk/public/resources/download")
    suspend fun getDownloadLink(
        @Query("public_key") publicKey: String,
        @Query("path") path: String? = null
    ): YandexDownloadResponse
}
