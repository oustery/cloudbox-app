package com.example.data.repository

import android.util.Log
import com.example.data.api.CloudMailRuApi
import com.example.data.api.CloudUrlParser
import com.example.data.api.NetworkModule
import com.example.data.local.SavedLinkDao
import com.example.data.local.SavedLinkEntity
import com.example.data.model.CloudItem
import com.example.data.model.FolderResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Size presets for the Mail.ru public-link thumbnail generator.
 *
 * The thumb shard accepts named quality tokens, not arbitrary pixel sizes:
 * https://thumb.cloud.mail.ru/weblink/thumb/{token}/{encoded_weblink_path}
 *
 * Confirmed against the live shard (October 2026):
 *  - xw0   -> high-resolution preview (long side ~1200 px) for images & videos
 *  - xw1   -> high-resolution preview, alternate crop
 *  - vw0   -> small preview (160x120), cheap for lists
 *  - vxw0  -> video poster frame (640x360)
 */
enum class ThumbnailSize(val token: String) {
    SMALL("vw0"),
    HIGH_RES("xw0"),
    VIDEO_POSTER("vxw0");
}

class CloudMailRuRepository(
    private val api: CloudMailRuApi = NetworkModule.api,
    private val savedLinkDao: SavedLinkDao
) {
    companion object {
        private const val TAG = "CloudMailRuRepo"
        // Reliable fallback shards for Mail.ru Cloud.
        // NOTE: the legacy /weblink/get/ endpoint was decommissioned server-side
        // (it returns nginx 404 on every clocloXX host). Both downloads and
        // playback now go through /weblink/view/, which is the only live weblink
        // content endpoint: it follows a signed 301 redirect, answers 206 with
        // "Accept-Ranges: bytes" and streams the file body.
        private const val DEFAULT_DOWNLOAD_SHARD = "https://cloclo60.cloud.mail.ru/weblink/view/"
        private const val DEFAULT_THUMB_SHARD = "https://thumb.cloud.mail.ru/weblink/thumb/"
        private const val DEFAULT_VIEW_SHARD = "https://cloclo60.cloud.mail.ru/weblink/view/"
    }

    private var cachedDownloadShard: String? = null
    private var cachedThumbShard: String? = null
    private var cachedViewShard: String? = null

    val allSavedLinks: Flow<List<SavedLinkEntity>> = savedLinkDao.getAllSavedLinks()

    suspend fun saveLink(
        url: String,
        title: String,
        category: String = "Все",
        itemCount: Int = 0,
        previewThumbnailUrl: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val weblinkKey = CloudUrlParser.extractWeblink(url) ?: url
        val existing = savedLinkDao.getByWeblink(weblinkKey)
        if (existing != null) {
            val updated = existing.copy(
                title = if (title.isNotBlank()) title else existing.title,
                category = category,
                itemCount = if (itemCount > 0) itemCount else existing.itemCount,
                previewThumbnailUrl = previewThumbnailUrl ?: existing.previewThumbnailUrl
            )
            savedLinkDao.updateLink(updated)
            existing.id
        } else {
            val entity = SavedLinkEntity(
                url = url,
                weblinkKey = weblinkKey,
                title = if (title.isNotBlank()) title else weblinkKey,
                category = category,
                itemCount = itemCount,
                previewThumbnailUrl = previewThumbnailUrl
            )
            savedLinkDao.insertLink(entity)
        }
    }

    suspend fun deleteLink(id: Long) = withContext(Dispatchers.IO) {
        savedLinkDao.deleteById(id)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        savedLinkDao.setFavorite(id, isFavorite)
    }

    /**
     * Updates shard URLs from the dispatcher API.
     */
    suspend fun refreshDispatcher(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.getDispatcher()
            val body = response.body
            if (body != null) {
                body.weblinkGet?.firstOrNull()?.url?.let {
                    cachedDownloadShard = ensureTrailingSlash(it)
                }
                body.weblinkThumbnails?.firstOrNull()?.url?.let {
                    cachedThumbShard = ensureTrailingSlash(it)
                }
                body.weblinkView?.firstOrNull()?.url?.let {
                    cachedViewShard = ensureTrailingSlash(it)
                }
                return@withContext true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dispatcher request failed, using default shards: ${e.message}")
        }
        false
    }

    private fun getDownloadShard(): String = cachedDownloadShard ?: DEFAULT_DOWNLOAD_SHARD
    private fun getThumbShard(): String = cachedThumbShard ?: DEFAULT_THUMB_SHARD
    private fun getViewShard(): String = cachedViewShard ?: DEFAULT_VIEW_SHARD

    private fun ensureTrailingSlash(url: String): String = if (url.endsWith("/")) url else "$url/"

    /**
     * Constructs a direct download URL for a cloud item.
     *
     * Uses the live /weblink/view/ endpoint (the old /weblink/get/ shard no
     * longer exists). OkHttp follows the signed 301 redirect transparently and
     * Range headers are honored, so resume/pause keeps working.
     */
    fun getDownloadUrl(weblink: String): String {
        val cleanWeblink = encodeWeblinkPath(weblink)
        return "${getDownloadShard()}$cleanWeblink"
    }

    /**
     * Constructs a streaming URL for video / audio playback.
     */
    fun getStreamingUrl(weblink: String): String {
        val cleanWeblink = encodeWeblinkPath(weblink)
        return "${getViewShard()}$cleanWeblink"
    }

    /**
     * Constructs a thumbnail image URL for an image or video file.
     *
     * The public thumb shard validates the size segment strictly: numeric sizes
     * such as "512" are rejected with HTTP 400 ("unknown size"). Only named
     * tokens (see [ThumbnailSize]) are accepted, and the weblink path must be
     * percent-encoded because it frequently contains '@', spaces, parentheses
     * and Cyrillic characters.
     */
    fun getThumbnailUrl(weblink: String, size: ThumbnailSize = ThumbnailSize.HIGH_RES): String {
        val cleanWeblink = encodeWeblinkPath(weblink)
        return "${getThumbShard()}${size.token}/$cleanWeblink"
    }

    /**
     * Percent-encodes every path segment of a weblink so the resulting URL is
     * safe for OkHttp / Coil. URLEncoder targets form encoding ('+' for space),
     * therefore '+' is converted back to '%20' for path semantics.
     */
    private fun encodeWeblinkPath(weblink: String): String {
        return weblink.trim('/').split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
    }

    /**
     * Fetches the contents of a public folder or file.
     */
    suspend fun fetchFolderContent(rawUrlOrWeblink: String): Result<FolderResult> = withContext(Dispatchers.IO) {
        val weblinkKey = CloudUrlParser.extractWeblink(rawUrlOrWeblink)
            ?: return@withContext Result.failure(IllegalArgumentException("Неверный формат ссылки Облака Mail.ru"))

        // Ensure shards are ready
        if (cachedDownloadShard == null) {
            refreshDispatcher()
        }

        try {
            val response: FolderResponse = api.getFolder(weblink = weblinkKey)
            val body = response.body
            val items = body?.list

            if (items != null && items.isNotEmpty()) {
                val folderName = body.name?.takeIf { it.isNotBlank() }
                    ?: weblinkKey.substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: "Облачная папка"

                return@withContext Result.success(
                    FolderResult(
                        weblinkKey = weblinkKey,
                        name = folderName,
                        items = items,
                        count = body.count,
                        total = body.total
                    )
                )
            }

            // If body.list is null or empty, it could be a single file link
            if (body != null && body.kind == "file") {
                val singleItem = CloudItem(
                    kind = "file",
                    name = body.name ?: weblinkKey.substringAfterLast('/'),
                    size = body.size ?: 0L,
                    type = body.type,
                    mtime = response.time ?: 0L,
                    weblink = weblinkKey
                )
                return@withContext Result.success(
                    FolderResult(
                        weblinkKey = weblinkKey,
                        name = singleItem.name,
                        items = listOf(singleItem),
                        count = null,
                        total = null
                    )
                )
            }

            // Fallback: Try web scraping HTML for tokens or public file info
            val fallbackResult = fallbackScrapeWeblink(weblinkKey)
            if (fallbackResult != null) {
                return@withContext Result.success(fallbackResult)
            }

            Result.failure(Exception("Содержимое папки пусто или недоступно"))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching folder for $weblinkKey", e)
            // Try fallback on network/parsing error
            val fallbackResult = fallbackScrapeWeblink(weblinkKey)
            if (fallbackResult != null) {
                return@withContext Result.success(fallbackResult)
            }
            Result.failure(Exception(formatErrorMessage(e)))
        }
    }

    private suspend fun fallbackScrapeWeblink(weblinkKey: String): FolderResult? {
        return try {
            val fullUrl = CloudUrlParser.toFullPublicUrl(weblinkKey)
            val resp = api.getHtmlPage(fullUrl)
            if (!resp.isSuccessful) return null
            val html = resp.body()?.string() ?: return null

            // Search for folder title in HTML
            val titleMatcher = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE).matcher(html)
            val title = if (titleMatcher.find()) {
                titleMatcher.group(1)?.replace("/ Облако Mail.ru", "")?.trim() ?: weblinkKey
            } else weblinkKey

            // Try to extract filename if single file
            val fileName = weblinkKey.substringAfterLast('/')
            val dummyItem = CloudItem(
                kind = if (fileName.contains('.')) "file" else "folder",
                name = fileName,
                size = 0L,
                type = null,
                mtime = System.currentTimeMillis() / 1000L,
                weblink = weblinkKey
            )
            FolderResult(
                weblinkKey = weblinkKey,
                name = title,
                items = listOf(dummyItem),
                count = null,
                total = null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Fallback scraping failed: ${e.message}")
            null
        }
    }

    private fun formatErrorMessage(e: Throwable): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        return when {
            msg.contains("404") -> "Ссылка не найдена или файл был удален с сервера"
            msg.contains("403") -> "Доступ к файлу ограничен (возможно, защищен паролем)"
            msg.contains("Unable to resolve host") || msg.contains("UnknownHostException") ->
                "Отсутствует подключение к интернету"
            msg.contains("timeout") || msg.contains("SocketTimeoutException") ->
                "Превышено время ожидания ответа от сервера Mail.ru"
            else -> "Не удалось загрузить папку: $msg"
        }
    }
}

data class FolderResult(
    val weblinkKey: String,
    val name: String,
    val items: List<CloudItem>,
    val count: com.example.data.model.FolderCount?,
    val total: com.example.data.model.FolderTotal?
)
