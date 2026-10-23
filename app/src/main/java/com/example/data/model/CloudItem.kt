package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@JsonClass(generateAdapter = true)
data class CloudItem(
    @Json(name = "kind") val kind: String = "file", // "folder" or "file"
    @Json(name = "name") val name: String = "",
    @Json(name = "size") val size: Long = 0L,
    @Json(name = "type") val type: String? = null, // "image", "video", "audio", "document", etc.
    @Json(name = "mtime") val mtime: Long = 0L,
    @Json(name = "weblink") val weblink: String = "",
    @Json(name = "count") val count: FolderCount? = null,
    @Json(name = "tree") val tree: String? = null,
    @Json(name = "duration") val durationSeconds: Long? = null,
    @Json(name = "video_duration") val videoDurationSeconds: Long? = null,
    @Json(name = "duration_ms") val durationMilliseconds: Long? = null
) {
    companion object {
        // Shared across all items: SimpleDateFormat construction is costly and these
        // getters run for every rendered row and every filter pass. UI/parse callers
        // are main-thread only, so one instance is safe.
        private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "tiff", "svg")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "webm", "flv", "wmv", "3gp", "m4v")
        private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma")
        private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
        private val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv")
    }

    val mediaDurationSeconds: Long?
        get() = durationSeconds
            ?: videoDurationSeconds
            ?: durationMilliseconds?.let { (it / 1000L).takeIf { seconds -> seconds > 0L } }

    val formattedDuration: String?
        get() = mediaDurationSeconds?.let { totalSeconds ->
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            if (hours > 0) "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
            else "%d:%02d".format(Locale.ROOT, minutes, seconds)
        }

    val isFolder: Boolean
        get() = kind.equals("folder", ignoreCase = true)

    val isImage: Boolean
        get() {
            if (isFolder) return false
            val t = type?.lowercase(Locale.ROOT) ?: ""
            if (t.contains("image") || t.contains("photo")) return true
            return fileExtension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS
        }

    val isVideo: Boolean
        get() {
            if (isFolder) return false
            val t = type?.lowercase(Locale.ROOT) ?: ""
            if (t.contains("video")) return true
            return fileExtension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS
        }

    val isAudio: Boolean
        get() {
            if (isFolder) return false
            val t = type?.lowercase(Locale.ROOT) ?: ""
            if (t.contains("audio")) return true
            return fileExtension.lowercase(Locale.ROOT) in AUDIO_EXTENSIONS
        }

    val isArchive: Boolean
        get() {
            if (isFolder) return false
            return fileExtension.lowercase(Locale.ROOT) in ARCHIVE_EXTENSIONS
        }

    val isDocument: Boolean
        get() {
            if (isFolder) return false
            return fileExtension.lowercase(Locale.ROOT) in DOCUMENT_EXTENSIONS
        }

    val fileExtension: String
        get() {
            val dotIndex = name.lastIndexOf('.')
            return if (dotIndex > 0 && dotIndex < name.length - 1) {
                name.substring(dotIndex + 1)
            } else ""
        }

    val formattedSize: String
        get() {
            if (isFolder) {
                val files = count?.files ?: 0
                val folders = count?.folders ?: 0
                return when {
                    folders > 0 && files > 0 -> "$files файлов, $folders папок"
                    folders > 0 -> "$folders папок"
                    files > 0 -> "$files файлов"
                    else -> "Папка"
                }
            }
            if (size <= 0) return "0 Б"
            val kb = size / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f ГБ", gb)
                mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f МБ", mb)
                kb >= 1.0 -> String.format(Locale.getDefault(), "%.1f КБ", kb)
                else -> "$size Б"
            }
        }

    val formattedDate: String
        get() {
            if (mtime <= 0) return ""
            return try {
                dateFormat.format(Date(mtime * 1000L))
            } catch (e: Exception) {
                ""
            }
        }
}

enum class CloudItemSortOrder(val title: String) {
    DATE_NEWEST("Дата: сначала новые"),
    DATE_OLDEST("Дата: сначала старые"),
    NAME_AZ("Имя: А–Я"),
    NAME_ZA("Имя: Я–А"),
    SIZE_LARGEST("Размер: сначала большие"),
    SIZE_SMALLEST("Размер: сначала маленькие"),
    DURATION_LONGEST("Длительность: сначала длинные")
}

/**
 * Order-specific comparator for cloud items. Unknown values (mtime = 0,
 * size = 0) sink to the tail of their group instead of polluting the top.
 */
fun cloudItemComparator(order: CloudItemSortOrder): Comparator<CloudItem> = when (order) {
    CloudItemSortOrder.DATE_NEWEST -> compareByDescending<CloudItem> { it.mtime }
        .thenBy { it.name.lowercase(Locale.ROOT) }
    CloudItemSortOrder.DATE_OLDEST -> compareBy<CloudItem> { it.mtime <= 0L }
        .thenBy { it.mtime }
        .thenBy { it.name.lowercase(Locale.ROOT) }
    CloudItemSortOrder.NAME_AZ -> compareBy<CloudItem> { it.name.lowercase(Locale.ROOT) }
    CloudItemSortOrder.NAME_ZA -> compareByDescending<CloudItem> { it.name.lowercase(Locale.ROOT) }
    CloudItemSortOrder.SIZE_LARGEST -> compareByDescending<CloudItem> { it.size }
        .thenBy { it.name.lowercase(Locale.ROOT) }
    CloudItemSortOrder.SIZE_SMALLEST -> compareBy<CloudItem> { it.size <= 0L }
        .thenBy { it.size }
        .thenBy { it.name.lowercase(Locale.ROOT) }
    CloudItemSortOrder.DURATION_LONGEST -> compareBy<CloudItem> { it.mediaDurationSeconds == null }
        .thenByDescending { it.mediaDurationSeconds ?: Long.MIN_VALUE }
        .thenBy { it.name.lowercase(Locale.ROOT) }
}

/**
 * Folders always come first (canonical file-manager behaviour); files inside
 * each group follow the chosen order.
 * Named [sortedByOrder] to avoid clashing with the stdlib Iterable.sortedBy.
 */
fun List<CloudItem>.sortedByOrder(order: CloudItemSortOrder): List<CloudItem> =
    sortedWith(compareBy<CloudItem> { !it.isFolder }.then(cloudItemComparator(order)))

@JsonClass(generateAdapter = true)
data class FolderCount(
    @Json(name = "folders") val folders: Int? = 0,
    @Json(name = "files") val files: Int? = 0
)

@JsonClass(generateAdapter = true)
data class FolderTotal(
    @Json(name = "folders") val folders: Int? = 0,
    @Json(name = "files") val files: Int? = 0,
    @Json(name = "size") val size: Long? = 0L
)
