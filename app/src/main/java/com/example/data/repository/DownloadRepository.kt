package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.api.NetworkModule
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTaskEntity
import com.example.data.model.CloudItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val cloudRepository: CloudMailRuRepository
) {
    companion object {
        private const val TAG = "DownloadRepo"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    val allDownloads: Flow<List<DownloadTaskEntity>> = downloadDao.getAllTasks()
    val activeDownloads: Flow<List<DownloadTaskEntity>> = downloadDao.getActiveTasks()

    fun enqueueDownload(item: CloudItem, customName: String? = null) {
        val fileName = sanitizeFileName(customName ?: item.name)
        val downloadUrl = cloudRepository.getDownloadUrl(item.weblink)
        val taskId = UUID.randomUUID().toString()

        val task = DownloadTaskEntity(
            id = taskId,
            weblink = item.weblink,
            downloadUrl = downloadUrl,
            fileName = fileName,
            fileSize = item.size,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED.name,
            mimeType = item.type ?: ""
        )

        repositoryScope.launch {
            downloadDao.insertTask(task)
            startTask(taskId, downloadUrl, fileName, item.size)
        }
    }

    fun enqueueBatch(items: List<CloudItem>) {
        items.forEach { item ->
            if (!item.isFolder) {
                enqueueDownload(item)
            }
        }
    }

    fun pauseDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        repositoryScope.launch {
            val task = downloadDao.getTaskById(id)
            if (task != null && task.status == DownloadStatus.DOWNLOADING.name) {
                downloadDao.updateProgress(id, task.downloadedBytes, 0L, DownloadStatus.PAUSED.name)
            }
        }
    }

    fun resumeDownload(id: String) {
        repositoryScope.launch {
            val task = downloadDao.getTaskById(id) ?: return@launch
            startTask(id, task.downloadUrl, task.fileName, task.fileSize, task.downloadedBytes)
        }
    }

    fun cancelDownload(id: String) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        repositoryScope.launch {
            downloadDao.deleteById(id)
        }
    }

    fun clearCompleted() {
        repositoryScope.launch {
            downloadDao.clearCompleted()
        }
    }

    private fun startTask(
        taskId: String,
        url: String,
        fileName: String,
        expectedSize: Long,
        startByte: Long = 0L
    ) {
        // Cancel existing job if any
        activeJobs[taskId]?.cancel()

        val job = repositoryScope.launch {
            downloadDao.updateProgress(taskId, startByte, 0L, DownloadStatus.DOWNLOADING.name)

            val targetDir = getDownloadsDirectory()
            val targetFile = File(targetDir, fileName)

            var inputStream: InputStream? = null
            var outputStream: FileOutputStream? = null

            try {
                val requestBuilder = Request.Builder().url(url)
                if (startByte > 0) {
                    requestBuilder.header("Range", "bytes=$startByte-")
                }

                val response = NetworkModule.okHttpClient.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) {
                    val errorMsg = "HTTP ${response.code}: ${response.message}"
                    downloadDao.setFailed(taskId, DownloadStatus.FAILED.name, errorMsg)
                    return@launch
                }

                val body = response.body
                if (body == null) {
                    downloadDao.setFailed(taskId, DownloadStatus.FAILED.name, "Пустой ответ от сервера")
                    return@launch
                }

                val totalLength = if (expectedSize > 0) expectedSize else (body.contentLength() + startByte)
                inputStream = body.byteStream()
                outputStream = FileOutputStream(targetFile, startByte > 0)

                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var currentDownloaded = startByte
                var lastUpdateTime = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    currentDownloaded += bytesRead
                    bytesSinceLastUpdate += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastUpdateTime

                    // Update UI/DB every 500ms
                    if (timeDiff >= 500) {
                        val speedBps = (bytesSinceLastUpdate * 1000) / timeDiff
                        downloadDao.updateProgress(taskId, currentDownloaded, speedBps, DownloadStatus.DOWNLOADING.name)
                        lastUpdateTime = now
                        bytesSinceLastUpdate = 0L
                    }
                }

                outputStream.flush()
                downloadDao.setCompleted(taskId, DownloadStatus.COMPLETED.name, targetFile.absolutePath)
                Log.d(TAG, "Download completed: ${targetFile.absolutePath}")
            } catch (e: CancellationException) {
                Log.d(TAG, "Download cancelled/paused: $taskId")
            } catch (e: Exception) {
                Log.e(TAG, "Download error for $taskId: ${e.message}", e)
                downloadDao.setFailed(taskId, DownloadStatus.FAILED.name, e.localizedMessage ?: "Ошибка скачивания")
            } finally {
                try {
                    inputStream?.close()
                } catch (ignored: Exception) {}
                try {
                    outputStream?.close()
                } catch (ignored: Exception) {}
                activeJobs.remove(taskId)
            }
        }

        activeJobs[taskId] = job
    }

    private fun getDownloadsDirectory(): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val cloudBoxDir = File(publicDownloads, "CloudBox")
        if (!cloudBoxDir.exists()) {
            cloudBoxDir.mkdirs()
        }
        return if (cloudBoxDir.exists() && cloudBoxDir.canWrite()) {
            cloudBoxDir
        } else {
            // App-specific external downloads directory as fallback
            val fallback = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            fallback.mkdirs()
            fallback
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
