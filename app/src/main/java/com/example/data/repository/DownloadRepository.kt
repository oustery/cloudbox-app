package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.api.LinkProvider
import com.example.data.api.NetworkModule
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTaskEntity
import com.example.data.model.CloudItem
import com.example.service.DownloadService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.coroutines.coroutineContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class DownloadRepository private constructor(
    context: Context,
    private val downloadDao: DownloadDao,
    private val cloudRepository: CloudMailRuRepository
) {
    companion object {
        private const val TAG = "DownloadRepo"
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1500L

        /** Parallel transfers cap: keeps weak mobile networks usable during batch downloads. */
        private const val MAX_PARALLEL_DOWNLOADS = 4

        @Volatile
        private var instance: DownloadRepository? = null

        /**
         * Process-wide instance. The foreground [DownloadService] and the UI must
         * observe and control the SAME set of active jobs, so the repository is
         * shared instead of being constructed per-ViewModel.
         */
        fun getInstance(context: Context): DownloadRepository {
            val appContext = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: DownloadRepository(
                    context = appContext,
                    downloadDao = AppDatabase.getDatabase(appContext).downloadDao(),
                    cloudRepository = CloudMailRuRepository(
                        savedLinkDao = AppDatabase.getDatabase(appContext).savedLinkDao()
                    )
                ).also { instance = it }
            }
        }
    }

    private val appContext: Context = context.applicationContext
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val downloadSlots = Semaphore(MAX_PARALLEL_DOWNLOADS)

    init {
        // Jobs live in this process only: anything still marked DOWNLOADING/QUEUED
        // in the database is a leftover from a previous run (e.g. process death).
        // Surface it as PAUSED so the user can resume instead of hitting a dead end.
        repositoryScope.launch {
            runCatching { downloadDao.getActiveTasksOnce() }.onSuccess { tasks ->
                tasks.forEach { stale ->
                    downloadDao.updateProgress(
                        stale.id,
                        stale.downloadedBytes,
                        0L,
                        DownloadStatus.PAUSED.name
                    )
                }
            }
        }
    }

    val allDownloads: Flow<List<DownloadTaskEntity>> = downloadDao.getAllTasks()
    val activeDownloads: Flow<List<DownloadTaskEntity>> = downloadDao.getActiveTasks()

    /** One-shot view of the active queue; used by the service before tearing down. */
    suspend fun activeDownloadsSnapshot(): List<DownloadTaskEntity> =
        downloadDao.getActiveTasksOnce()

    fun enqueueDownload(item: CloudItem, customName: String? = null) {
        val fileName = sanitizeFileName(customName ?: item.name)
        val taskId = UUID.randomUUID().toString()

        repositoryScope.launch {
            // Yandex hrefs are signed and must be resolved via the public API;
            // Mail.ru URLs are built synchronously from the shard.
            val downloadUrl = try {
                cloudRepository.resolveDirectUrl(item.weblink)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to resolve download URL for ${item.weblink}: ${t.message}", t)
                downloadDao.insertTask(
                    DownloadTaskEntity(
                        id = taskId,
                        weblink = item.weblink,
                        downloadUrl = "",
                        fileName = fileName,
                        fileSize = item.size,
                        downloadedBytes = 0L,
                        status = DownloadStatus.FAILED.name,
                        mimeType = item.type ?: "",
                        errorMessage = t.localizedMessage ?: "Не удалось получить адрес загрузки"
                    )
                )
                return@launch
            }

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

            downloadDao.insertTask(task)
            startTask(taskId, downloadUrl, fileName, item.size)
            DownloadService.start(appContext)
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
        activeJobs.remove(id)?.cancel()
        repositoryScope.launch {
            val task = downloadDao.getTaskById(id)
            if (task != null && (task.status == DownloadStatus.DOWNLOADING.name || task.status == DownloadStatus.QUEUED.name)) {
                downloadDao.updateProgress(id, task.downloadedBytes, 0L, DownloadStatus.PAUSED.name)
            }
        }
    }

    /** Pauses every active or queued transfer; used by the notification action. */
    fun pauseAll() {
        val ids = activeJobs.keys.toList()
        if (ids.isEmpty()) return
        ids.forEach { pauseDownload(it) }
    }

    fun resumeDownload(id: String) {
        repositoryScope.launch {
            val task = downloadDao.getTaskById(id) ?: return@launch
            if (task.status == DownloadStatus.DOWNLOADING.name || task.status == DownloadStatus.QUEUED.name) return@launch
            // Signed Yandex hrefs expire; re-resolve them before resuming.
            val url = if (CloudMailRuRepository.providerOf(task.weblink) == LinkProvider.YANDEX) {
                runCatching { cloudRepository.resolveDirectUrl(task.weblink) }.getOrNull() ?: task.downloadUrl
            } else {
                task.downloadUrl
            }
            startTask(id, url, task.fileName, task.fileSize, task.downloadedBytes)
            DownloadService.start(appContext)
        }
    }

    fun cancelDownload(id: String) {
        activeJobs.remove(id)?.cancel()
        repositoryScope.launch {
            downloadDao.deleteById(id)
        }
    }

    /** Cancels every active/queued transfer and drops them from the queue. */
    fun cancelAll() {
        val ids = activeJobs.keys.toList()
        ids.forEach { cancelDownload(it) }
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
            downloadDao.updateProgress(taskId, startByte, 0L, DownloadStatus.QUEUED.name)

            // Bound concurrency: batch downloads must not saturate the radio.
            downloadSlots.acquire()
            try {
                downloadDao.updateProgress(taskId, startByte, 0L, DownloadStatus.DOWNLOADING.name)

                val targetDir = getDownloadsDirectory()
                val targetFile = File(targetDir, fileName)

                var attempt = 0
                var resumeFrom = startByte

                while (attempt < MAX_ATTEMPTS) {
                    attempt++
                    try {
                        runDownloadAttempt(taskId, url, targetFile, expectedSize, resumeFrom)
                        return@launch // completed successfully
                    } catch (e: CancellationException) {
                        Log.d(TAG, "Download cancelled/paused: $taskId")
                        throw e
                    } catch (e: Exception) {
                        // Persist whatever bytes we already got so the next attempt
                        // resumes instead of restarting the multi-hundred-MB file.
                        resumeFrom = targetFile.length().coerceAtLeast(0L)
                        if (attempt >= MAX_ATTEMPTS) {
                            Log.e(TAG, "Download failed after $attempt attempts: ${e.message}", e)
                            downloadDao.setFailed(
                                taskId,
                                DownloadStatus.FAILED.name,
                                e.localizedMessage ?: "Ошибка скачивания"
                            )
                        } else {
                            Log.w(TAG, "Attempt $attempt failed for $taskId (${e.message}); retrying from $resumeFrom")
                            downloadDao.updateProgress(
                                taskId,
                                resumeFrom,
                                0L,
                                DownloadStatus.DOWNLOADING.name
                            )
                            delay(RETRY_DELAY_MS * attempt)
                        }
                    }
                }
            } finally {
                downloadSlots.release()
            }
        }

        activeJobs[taskId] = job
    }

    /**
     * Streams one HTTP attempt into [targetFile].
     *
     * The public Mail.ru view endpoint (used for downloads since the legacy
     * /weblink/get/ shard was decommissioned) honors byte ranges, so resume
     * requests carry a Range header. If the server answers with 200 instead of
     * 206 (range ignored or file replaced), the partially downloaded file is
     * truncated and the transfer restarts from zero.
     */
    private suspend fun runDownloadAttempt(
        taskId: String,
        url: String,
        targetFile: File,
        expectedSize: Long,
        startByte: Long
    ) {
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
                return
            }

            val body = response.body
            if (body == null) {
                downloadDao.setFailed(taskId, DownloadStatus.FAILED.name, "Пустой ответ от сервера")
                return
            }

            val rangeAccepted = startByte > 0 && response.code == 206
            val effectiveStart = if (rangeAccepted) startByte else 0L

            val totalLength = if (expectedSize > 0) {
                expectedSize
            } else {
                body.contentLength() + effectiveStart
            }
            inputStream = body.byteStream()
            outputStream = FileOutputStream(targetFile, rangeAccepted)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var currentDownloaded = effectiveStart
            var lastUpdateTime = System.currentTimeMillis()
            var bytesSinceLastUpdate = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                // Cancellation-aware: pause/cancel must stop the blocking read loop.
                coroutineContext.ensureActive()

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
        } finally {
            try {
                inputStream?.close()
            } catch (ignored: Exception) {}
            try {
                outputStream?.close()
            } catch (ignored: Exception) {}
        }
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
            val fallback = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: appContext.filesDir
            fallback.mkdirs()
            fallback
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
