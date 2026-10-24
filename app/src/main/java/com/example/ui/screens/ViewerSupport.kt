package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.example.ui.theme.BrandAccent
import androidx.compose.ui.graphics.Color

/** Brand accent (rose) tuned for legibility on top of video frames in both themes. */
internal val PlayerAccent = BrandAccent
internal val PlayerOnSurface = Color.White

internal const val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

internal val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Process-wide LRU media cache. Streams downloaded from the Mail.ru shards are
 * stored here so that seek-backs, replays and re-opened videos start instantly
 * instead of re-fetching megabytes over the high-latency public endpoints.
 */
@AndroidXOptIn(UnstableApi::class)
object PlayerMediaCache {
    private const val MAX_CACHE_BYTES = 256L * 1024 * 1024

    @Volatile
    private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache = instance ?: synchronized(this) {
        instance ?: SimpleCache(
            java.io.File(context.cacheDir, "cloudbox_media_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context)
        ).also { instance = it }
    }

    /** Bytes currently occupied by the streamed-media cache (0 if never used). */
    fun sizeBytes(): Long = instance?.cacheSpace ?: 0L

    /**
     * Drops every cached stream. Safe to call when no playback is running;
     * errors are swallowed so the settings screen never crashes on a locked
     * cache file. Returns true when the wipe reported success.
     */
    fun clear(): Boolean {
        val cache = instance ?: return true
        return try {
            cache.keys.toList().forEach { key ->
                runCatching { cache.removeResource(key) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

internal fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
