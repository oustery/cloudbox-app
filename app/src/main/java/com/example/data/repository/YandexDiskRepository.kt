package com.example.data.repository

import android.util.Log
import com.example.data.api.NetworkModule
import com.example.data.api.YandexDiskApi
import com.example.data.api.YandexUrlParser
import com.example.data.model.CloudItem
import com.example.data.model.FolderTotal
import com.example.data.model.YandexPublicResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Repository for PUBLIC Yandex.Disk resources.
 *
 * The public API (cloud-api.yandex.net) works anonymously: the public link
 * itself serves as the key. Two resource shapes are handled:
 *  - "dir" — paginated listing of the public folder (limit 200/page, capped);
 *  - "file" — a single-file public link (or a nested file path).
 *
 * Every item gets a stable internal weblink: the normalized public URL plus
 * the item path inside the folder
 * ("https://disk.yandex.ru/d/abc/sub/file.jpg"), which is all that is needed
 * to resolve downloads, streaming and previews later.
 */
class YandexDiskRepository(
    private val api: YandexDiskApi = NetworkModule.yandexApi
) {
    companion object {
        private const val TAG = "YandexDiskRepo"
        private const val PAGE_SIZE = 200
        /** Hard cap so a hostile/abnormal listing cannot spin forever. */
        private const val MAX_ITEMS = 2000
        private const val PREVIEW_SIZE_TOKEN = "XL"

        /**
         * Thread-local parser: Yandex timestamps are ISO-8601 with a zone
         * ("2026-01-01T12:34:56+03:00" / "...Z"). SimpleDateFormat('X') is
         * available since API 24; fractional seconds are stripped manually
         * because the pattern cannot match variable-length fractions.
         */
        private val isoParser = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX", Locale.US).apply { isLenient = false }
        }
    }

    /**
     * Fetches a public Yandex.Disk folder (or a single public file) and maps
     * it into the provider-neutral [FolderResult].
     */
    suspend fun fetchFolderContent(rawUrlOrWeblink: String): Result<FolderResult> =
        withContext(Dispatchers.IO) {
            val parsed = YandexUrlParser.parse(rawUrlOrWeblink)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Неверный формат ссылки Яндекс.Диска")
                )

            try {
                val root = api.getPublicResource(
                    publicKey = parsed.publicKey,
                    path = parsed.subPath.ifBlank { null }
                )

                if (root.type.equals("file", ignoreCase = true)) {
                    val item = mapResource(root, parsed.publicKey, parsed.subPath, isRoot = true)
                    return@withContext Result.success(
                        FolderResult(
                            weblinkKey = parsed.publicKey + parsed.subPath,
                            name = item.name.ifBlank { "Файл Яндекс.Диска" },
                            items = listOf(item),
                            count = null,
                            total = null
                        )
                    )
                }

                val embedded = root.embedded
                val resources = mutableListOf<YandexPublicResource>()
                embedded?.items?.let { resources.addAll(it) }
                val total = embedded?.total ?: resources.size
                var offset = resources.size

                while (resources.isNotEmpty() && offset < total && offset < MAX_ITEMS) {
                    val page = api.getPublicResource(
                        publicKey = parsed.publicKey,
                        path = parsed.subPath.ifBlank { null },
                        offset = offset
                    )
                    val batch = page.embedded?.items.orEmpty()
                    if (batch.isEmpty()) break
                    resources.addAll(batch)
                    offset += batch.size
                }

                if (resources.isEmpty()) {
                    return@withContext Result.failure(
                        Exception("Папка Яндекс.Диска пуста или недоступна")
                    )
                }

                val items = resources.map { mapResource(it, parsed.publicKey, parsed.subPath) }
                val folderName = root.name?.takeIf { it.isNotBlank() }
                    ?: parsed.publicKey.substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: "Папка Яндекс.Диска"

                Result.success(
                    FolderResult(
                        weblinkKey = parsed.publicKey + parsed.subPath,
                        name = folderName,
                        items = items,
                        count = null,
                        total = FolderTotal(
                            folders = items.count { it.isFolder },
                            files = items.count { !it.isFolder },
                            size = null
                        )
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching Yandex resource for ${parsed.publicKey}", e)
                Result.failure(Exception(formatErrorMessage(e)))
            }
        }

    /**
     * Resolves a signed, time-limited direct download/streaming URL.
     * The href supports byte ranges, so downloads resume and ExoPlayer seeks.
     */
    suspend fun resolveDirectUrl(weblink: String): String = withContext(Dispatchers.IO) {
        val parsed = YandexUrlParser.parse(weblink)
            ?: throw IllegalArgumentException("Неверный формат ссылки Яндекс.Диска")
        val response = api.getDownloadLink(
            publicKey = parsed.publicKey,
            path = parsed.subPath.ifBlank { null }
        )
        response.href.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Яндекс.Диск не вернул адрес загрузки")
    }

    /**
     * Rewrites the named size token of a Yandex preview URL.
     * Returns null when the item has no preview (non-media files).
     */
    fun thumbnailUrl(previewUrl: String?, size: ThumbnailSize): String? {
        if (previewUrl.isNullOrBlank()) return null
        val token = when (size) {
            ThumbnailSize.SMALL -> "M"
            ThumbnailSize.HIGH_RES -> PREVIEW_SIZE_TOKEN
            ThumbnailSize.VIDEO_POSTER -> PREVIEW_SIZE_TOKEN
        }
        return if (previewUrl.contains("size=")) {
            previewUrl.replace(Regex("size=[^&]+"), "size=$token")
        } else {
            previewUrl + (if (previewUrl.contains('?')) "&" else "?") + "size=$token"
        }
    }

    /**
     * Maps one API resource to the provider-neutral [CloudItem].
     *
     * The item weblink is composed from OUR pagination state (parent dir path
     * + item name) rather than from Yandex `path` fields, whose exact format
     * differs between listing modes.
     */
    internal fun mapResource(
        resource: YandexPublicResource,
        publicKey: String,
        parentPath: String,
        isRoot: Boolean = false
    ): CloudItem {
        val isDir = resource.type.equals("dir", ignoreCase = true)
        val name = resource.name?.takeIf { it.isNotBlank() } ?: "Без названия"
        val itemPath = if (isRoot) {
            parentPath
        } else {
            val trimmedParent = parentPath.trimEnd('/')
            "$trimmedParent/$name"
        }
        return CloudItem(
            kind = if (isDir) "folder" else "file",
            name = name,
            size = resource.size,
            type = mapMediaType(resource),
            mtime = isoToEpochSeconds(resource.modified) ?: isoToEpochSeconds(resource.created) ?: 0L,
            weblink = YandexUrlParser.buildItemWeblink(publicKey, itemPath),
            previewUrl = resource.preview
        )
    }

    /** media_type carries the friendly category; mime_type is the fallback. */
    private fun mapMediaType(resource: YandexPublicResource): String? {
        resource.mediaType?.lowercase()?.let { media ->
            return if (media in setOf("image", "video", "audio")) media else null
        }
        val mime = resource.mimeType?.lowercase() ?: return null
        return when {
            mime.startsWith("image/") -> "image"
            mime.startsWith("video/") -> "video"
            mime.startsWith("audio/") -> "audio"
            else -> null
        }
    }

    private fun isoToEpochSeconds(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            var value = iso.trim()
            val timeIndex = value.indexOf('T')
            val dotIndex = value.indexOf('.', if (timeIndex >= 0) timeIndex else 0)
            if (dotIndex >= 0) {
                // Drop the fractional part, keep the trailing zone (Z / +HH:MM).
                val zoneStart = listOf(
                    value.indexOf('Z', dotIndex),
                    value.indexOf('+', dotIndex),
                    value.indexOf('-', dotIndex + 1)
                ).filter { it > dotIndex }.minOrNull() ?: value.length
                value = value.substring(0, dotIndex) + value.substring(zoneStart)
            }
            isoParser.get()?.parse(value)?.time?.div(1000L)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatErrorMessage(e: Throwable): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        return when {
            msg.contains("400") -> "Некорректная ссылка Яндекс.Диска"
            msg.contains("404") -> "Ссылка не найдена или файл был удален с Яндекс.Диска"
            msg.contains("403") -> "Доступ к ресурсу ограничен (возможно, защищен паролем)"
            msg.contains("Unable to resolve host") || msg.contains("UnknownHostException") ->
                "Отсутствует подключение к интернету"
            msg.contains("timeout") || msg.contains("SocketTimeoutException") ->
                "Превышено время ожидания ответа от Яндекс.Диска"
            else -> "Не удалось загрузить папку: $msg"
        }
    }
}
