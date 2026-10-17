package com.example.data.api

import com.example.data.model.DispatcherResponse
import com.example.data.model.FolderResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface CloudMailRuApi {

    @GET("api/v2/dispatcher")
    suspend fun getDispatcher(): DispatcherResponse

    @GET("api/v2/folder")
    suspend fun getFolder(
        @Query("weblink") weblink: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 1000,
        @Query("api") api: Int = 2
    ): FolderResponse

    @Streaming
    @GET
    suspend fun streamFile(
        @Url url: String,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>

    @GET
    suspend fun getHtmlPage(@Url url: String): Response<ResponseBody>
}
