package com.example.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.example.data.api.NetworkModule
import com.example.data.local.SavedLinkEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Phases of a ZIP-archive job, mirrored into [ArchiveProgress.phase].
 */
enum class ArchivePhase { COLLECTING, DOWNLOADING, DONE, FAILED, CANCELLED }

/** Immutable snapshot of what the archive job is doing right now. */
data class ArchiveProgress(
    val phase: ArchivePhase,
    val currentLinkTitle: String? = null,
    val linksTotal: Int = 0,
    val linksDone: Int = 0,
    val filesDone: Int = 0,
    val filesTotal: Int = 0,
    val filesFailed: Int = 0,
    val bytesTotal: Long = 0L,
    val bytesWritten: Long = 0L,
    val outputFileName: String? = null,
    val truncated: Boolean = false,
    val errorMessage: String? = null
) {
    val isActive: Boolean get() = phase == ArchivePhase.COLLECTING || phase == ArchivePhase.DOWNLOADING
    val downloadFraction: Float?
        get() = if (filesTotal > 0 && bytesTotal > 0L) {
            (bytesWritten.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f)
        } else null
}

/** One file scheduled to be packed into the archive. */
private data class ArchiveFilePlan(
    val linkTitle: String,
    val relativePath: String,
    val weblink: String,
    val size: Long
)

/**
 * Packs the contents of one or more saved links into a single ZIP archive.
 *
 * The job walks each link's folder tree via [CloudMailRuRepository.fetchFolderContent]
 * (both Mail.ru and Yandex public links are supported), streams every file
 * straight into a [ZipOutputStream] — no intermediate temp files — and writes
 * the result into `Downloads/CloudBox`.
 *
 * Guards keep the job sane on mobile networks: a cap on file count, total
 * bytes and recursion depth. Files that fail to resolve/download are skipped
 * and counted instead of failing the whole archive.
 *
 * Only one job runs at a time (per process); [cancel] stops collection and
 * download, deleting the partially written archive.
 */
class ArchiveRepository private constructor(
    context: Context,
    private val cloudRepository: CloudMailRuRepository
) {
    companion object {
        private const val TAG = "ArchiveRepo"

        /** Hard caps so a stray "archive everything" tap cannot fill the disk. */
        const val MAX_FILES = 400
        const val MAX_TOTAL_BYTES = 3L * 1024 * 1024 * 1024 // 3 GB
        const val MAX_DEPTH = 6

        private const val BUFFER_SIZE = 128 * 1024

        @Volatile
        private var instance: ArchiveRepository? = null

        fun getInstance(context: Context): ArchiveRepository {
            val appContext = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: ArchiveRepository(
                    context = appContext,
                    cloudRepository = CloudMailRuRepository(
                        savedLinkDao = com.example.data.local.AppDatabase.getDatabase(appContext).savedLinkDao()
                    )
                ).also { instance = it }
            }
        }
    }

    private val appContext: Context = context.applicationContext
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _progress = MutableStateFlow<ArchiveProgress?>(null)
    val progress: StateFlow<ArchiveProgress?> = _progress.asStateFlow()

    /** Starts packing [links]; ignored while another job is running. */
    fun archiveLinks(links: List<SavedLinkEntity>) {
        if (links.isEmpty()) return
        if (_progress.value?.isActive == true) return
        job = repositoryScope.launch {
            runArchive(links)
        }
    }

    /** Cancels the running job and removes its partial output. */
    fun cancel() {
        job?.cancel()
        job = null
    }

    /** Clears the finished job from the state (UI shows the result via snackbar). */
    fun dismissResult() {
        if (_progress.value?.isActive != true) _progress.value = null
    }

    private suspend fun runArchive(links: List<SavedLinkEntity>) {
        val started = System.currentTimeMillis()
        _progress.value = ArchiveProgress(
            phase = ArchivePhase.COLLECTING,
            linksTotal = links.size
        )
        try {
            // 1. Walk every link's tree and build the flat file plan.
            val plans = mutableListOf<ArchiveFilePlan>()
            var truncated = false
            var collectionFailed = 0
            for ((index, link) in links.withIndex()) {
                coroutineContext.ensureActive()
                _progress.update {
                    it?.copy(currentLinkTitle = link.title, linksDone = index)
                }
                val added = collectFiles(
                    weblink = link.weblinkKey,
                    linkTitle = link.title,
                    relativePath = "",
                    depth = 0,
                    plans = plans
                ).also { truncated = truncated || it.truncated }
                if (!added.anyFound) collectionFailed++
            }
            _progress.update { it?.copy(truncated = truncated, linksDone = links.size) }

            if (plans.isEmpty()) {
                _progress.value = ArchiveProgress(
                    phase = ArchivePhase.FAILED,
                    linksTotal = links.size,
                    errorMessage = if (collectionFailed > 0)
                        "Не удалось получить содержимое выбранных ссылок"
                    else
                        "В выбранных ссылках нет файлов"
                )
                return
            }

            val totalBytes = plans.sumOf { it.size }
            if (totalBytes > MAX_TOTAL_BYTES) {
                _progress.value = ArchiveProgress(
                    phase = ArchivePhase.FAILED,
                    linksTotal = links.size,
                    filesTotal = plans.size,
                    bytesTotal = totalBytes,
                    errorMessage = "Суммарный размер превышает 3 ГБ — выберите меньше ссылок"
                )
                return
            }

            // 2. Stream every file into the ZIP.
            val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(started))
            val zipName = "cloudbox-archive-$stamp.zip"
            _progress.update {
                it?.copy(
                    phase = ArchivePhase.DOWNLOADING,
                    filesTotal = plans.size,
                    bytesTotal = totalBytes,
                    outputFileName = zipName
                )
            }

            val outFile = File(getDownloadsDirectory(), zipName)
            try {
                writeZip(outFile, plans)
            } catch (ce: CancellationException) {
                outFile.delete()
                throw ce
            } catch (t: Throwable) {
                Log.e(TAG, "Archive failed: ${t.message}", t)
                outFile.delete()
                _progress.value = ArchiveProgress(
                    phase = ArchivePhase.FAILED,
                    linksTotal = links.size,
                    errorMessage = t.message ?: "Не удалось создать архив"
                )
                return
            }

            _progress.value = _progress.value?.copy(
                phase = ArchivePhase.DONE,
                outputFileName = zipName
            )
        } catch (ce: CancellationException) {
            _progress.value = ArchiveProgress(
                phase = ArchivePhase.CANCELLED,
                linksTotal = links.size
            )
            throw ce
        }
    }

    /** Streams every planned file into [outFile]; throws on I/O errors. */
    private suspend fun writeZip(outFile: File, plans: List<ArchiveFilePlan>) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zip ->
            zip.setLevel(Deflater.BEST_SPEED)
            for ((index, plan) in plans.withIndex()) {
                coroutineContext.ensureActive()
                _progress.update { it?.copy(currentLinkTitle = plan.linkTitle) }
                val ok = try {
                    writeZipEntry(zip, plan)
                    true
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "Skip ${plan.relativePath}: ${t.message}")
                    false
                }
                if (!ok) {
                    _progress.update { it?.copy(filesFailed = it.filesFailed + 1) }
                }
                _progress.update { it?.copy(filesDone = index + 1) }
            }
            zip.finish()
        }
    }

    /** Downloads one file and copies it into the archive, updating byte progress. */
    private suspend fun writeZipEntry(zip: ZipOutputStream, plan: ArchiveFilePlan) {
        val url = cloudRepository.resolveDirectUrl(plan.weblink)
        val request = Request.Builder().url(url).build()
        NetworkModule.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val body = response.body ?: throw IllegalStateException("Пустой ответ")

            val entryName = sanitizeEntryName("${plan.linkTitle}/${plan.relativePath}")
            zip.putNextEntry(ZipEntry(entryName))
            val input = body.byteStream()
            val buffer = ByteArray(BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                coroutineContext.ensureActive()
                zip.write(buffer, 0, read)
                _progress.update { it?.copy(bytesWritten = it.bytesWritten + read) }
            }
            zip.closeEntry()
        }
    }

    private class CollectResult(val anyFound: Boolean, val truncated: Boolean)

    /**
     * Recursively walks the folder tree of [weblink], appending file plans.
     * Stops early when [MAX_FILES] is reached; [MAX_DEPTH] guards against
     * pathological nesting.
     */
    private suspend fun collectFiles(
        weblink: String,
        linkTitle: String,
        relativePath: String,
        depth: Int,
        plans: MutableList<ArchiveFilePlan>
    ): CollectResult {
        var anyFound = false
        var truncated = false
        if (depth > MAX_DEPTH || plans.size >= MAX_FILES) return CollectResult(false, true)

        val result = cloudRepository.fetchFolderContent(weblink)
        val folder = result.getOrNull() ?: return CollectResult(false, false)

        for (item in folder.items) {
            coroutineContext.ensureActive()
            if (plans.size >= MAX_FILES) {
                truncated = true
                break
            }
            val childPath = if (relativePath.isBlank()) item.name else "$relativePath/${item.name}"
            when {
                item.isFolder -> {
                    val child = collectFiles(
                        weblink = item.weblink,
                        linkTitle = linkTitle,
                        relativePath = childPath,
                        depth = depth + 1,
                        plans = plans
                    )
                    anyFound = anyFound || child.anyFound
                    truncated = truncated || child.truncated
                }
                else -> {
                    if (item.size >= 0 && item.name.isNotBlank()) {
                        plans.add(
                            ArchiveFilePlan(
                                linkTitle = linkTitle,
                                relativePath = childPath,
                                weblink = item.weblink,
                                size = item.size
                            )
                        )
                        anyFound = true
                    }
                }
            }
        }
        return CollectResult(anyFound, truncated)
    }

    /** Same policy as DownloadRepository: public Downloads/CloudBox with a fallback. */
    private fun getDownloadsDirectory(): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val cloudBoxDir = File(publicDownloads, "CloudBox")
        if (!cloudBoxDir.exists()) cloudBoxDir.mkdirs()
        return if (cloudBoxDir.exists() && cloudBoxDir.canWrite()) {
            cloudBoxDir
        } else {
            val fallback = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: appContext.filesDir
            fallback.mkdirs()
            fallback
        }
    }

    /** Zip-safe entry names: separators kept, forbidden characters replaced. */
    private fun sanitizeEntryName(name: String): String =
        name.replace(Regex("[\\\\:*?\"<>|]"), "_")
}
