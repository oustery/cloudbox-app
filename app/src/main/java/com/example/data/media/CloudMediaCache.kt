package com.example.data.media

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import com.example.data.api.NetworkModule
import java.io.File

@UnstableApi
object CloudMediaCache {
    private const val MAX_VIDEO_CACHE_BYTES = 512L * 1024L * 1024L
    private const val CACHE_DIRECTORY = "cloudbox-video-cache"

    @Volatile
    private var mediaCache: SimpleCache? = null

    fun dataSourceFactory(context: Context): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getOrCreateCache(context))
            .setUpstreamDataSourceFactory(OkHttpDataSource.Factory(NetworkModule.okHttpClient))
    }

    private fun getOrCreateCache(context: Context): SimpleCache {
        mediaCache?.let { return it }
        return synchronized(this) {
            mediaCache ?: SimpleCache(
                File(context.applicationContext.cacheDir, CACHE_DIRECTORY),
                LeastRecentlyUsedCacheEvictor(MAX_VIDEO_CACHE_BYTES),
                StandaloneDatabaseProvider(context.applicationContext)
            ).also { mediaCache = it }
        }
    }
}
