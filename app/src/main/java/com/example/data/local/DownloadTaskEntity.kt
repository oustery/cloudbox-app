package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,
    val weblink: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = DownloadStatus.QUEUED.name,
    val destinationPath: String = "",
    val mimeType: String = "",
    val speedBps: Long = 0L,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val progressPercent: Float
        get() = if (fileSize > 0) (downloadedBytes.toFloat() / fileSize.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedProgress: String
        get() = "${(progressPercent * 100).toInt()}%"

    val formattedSpeed: String
        get() {
            if (speedBps <= 0) return "0 КБ/с"
            val kb = speedBps / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(Locale.getDefault(), "%.1f МБ/с", mb)
            } else {
                String.format(Locale.getDefault(), "%.0f КБ/с", kb)
            }
        }

    val formattedDownloaded: String
        get() {
            val mbDownloaded = downloadedBytes / (1024.0 * 1024.0)
            val mbTotal = fileSize / (1024.0 * 1024.0)
            return if (mbTotal >= 1.0) {
                String.format(Locale.getDefault(), "%.1f / %.1f МБ", mbDownloaded, mbTotal)
            } else {
                val kbDownloaded = downloadedBytes / 1024.0
                val kbTotal = fileSize / 1024.0
                String.format(Locale.getDefault(), "%.0f / %.0f КБ", kbDownloaded, kbTotal)
            }
        }
}
