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
    @Json(name = "tree") val tree: String? = null
) {
    val isFolder: Boolean
        get() = kind.equals("folder", ignoreCase = true)

    val isImage: Boolean
        get() {
            if (isFolder) return false
            val t = type?.lowercase(Locale.ROOT) ?: ""
            if (t.contains("image") || t.contains("photo")) return true
            val ext = fileExtension.lowercase(Locale.ROOT)
            return ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "tiff", "svg")
        }

    val isVideo: Boolean
        get() {
            if (isFolder) return false
            val t = type?.lowercase(Locale.ROOT) ?: ""
            if (t.contains("video")) return true
            val ext = fileExtension.lowercase(Locale.ROOT)
            return ext in listOf("mp4", "mkv", "mov", "avi", "webm", "flv", "wmv", "3gp", "m4v")
        }

    val isAudio: Boolean
        get() {
            if (isFolder) return false
            val t = type?.lowercase(Locale.ROOT) ?: ""
            if (t.contains("audio")) return true
            val ext = fileExtension.lowercase(Locale.ROOT)
            return ext in listOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "wma")
        }

    val isArchive: Boolean
        get() {
            if (isFolder) return false
            val ext = fileExtension.lowercase(Locale.ROOT)
            return ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
        }

    val isDocument: Boolean
        get() {
            if (isFolder) return false
            val ext = fileExtension.lowercase(Locale.ROOT)
            return ext in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv")
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
                val date = Date(mtime * 1000L)
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                sdf.format(date)
            } catch (e: Exception) {
                ""
            }
        }
}

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
