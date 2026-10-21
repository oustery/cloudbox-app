package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.example.data.api.NetworkModule

class CloudBoxApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .okHttpClient { NetworkModule.okHttpClient }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("cloudbox-image-cache"))
                .maxSizeBytes(MAX_IMAGE_CACHE_BYTES)
                .build()
        }
        .build()

    private companion object {
        const val MAX_IMAGE_CACHE_BYTES = 64L * 1024L * 1024L
    }
}
