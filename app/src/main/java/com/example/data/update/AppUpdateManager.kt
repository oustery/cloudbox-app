package com.example.data.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Information about a newer build published in the repository's rolling `latest` release.
 */
data class AppUpdateInfo(
    val title: String,
    val buildNumber: Long,
    val commitSha: String,
    val publishedAtMs: Long,
    val publishedAtIso: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSizeBytes: Long,
    val releasePageUrl: String
)

/** UI-facing state of the update flow; rendered by [com.example.ui.components.UpdateBanner]. */
sealed interface UpdateUiState {
    /** Nothing to show. */
    data object Idle : UpdateUiState

    /** A newer build is available for download. */
    data class Available(val info: AppUpdateInfo) : UpdateUiState

    /** APK is being downloaded by the system DownloadManager. */
    data object Downloading : UpdateUiState

    /** APK downloaded and verified; ready to be installed. */
    data object ReadyToInstall : UpdateUiState

    /** Installed build is the newest one; banner stays hidden. */
    data object UpToDate : UpdateUiState
}

/**
 * Checks the repository's rolling GitHub release, downloads the matching APK through the
 * system DownloadManager and hands it to the package installer.
 *
 * The release body must contain a machine-readable marker written by the CD workflow:
 * `cloudbox-build sha=<commit> run=<run_number>` where run_number equals the APK's
 * versionCode. Releases without the marker (legacy) are compared by commit sha and
 * publish date instead.
 */
object AppUpdateManager {

    private const val RELEASE_API_URL =
        "https://api.github.com/repos/oustery/cloudbox-app/releases/tags/latest"
    private const val RELEASE_PAGE_URL =
        "https://github.com/oustery/cloudbox-app/releases/latest"
    const val APK_FILE_NAME = "CloudBox-update.apk"

    private const val CLOCK_SKEW_MS = 10 * 60 * 1000L

    private val markerRegex = Regex("""cloudbox-build\s+sha=([0-9a-fA-F]{7,40})\s+run=(\d+)""")
    private val titleShaRegex = Regex("""\(([0-9a-fA-F]{7,40})\)""")

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    @Volatile
    private var lastEnqueueId: Long = -1L

    @Volatile
    private var hasCheckedThisProcess: Boolean = false

    private var lastInfo: AppUpdateInfo? = null

    /**
     * Fetches the rolling release and updates [uiState]. Safe to call from the main
     * thread; performs the network request on [Dispatchers.IO]. Runs at most once per
     * app process so switching tabs does not hammer the GitHub API.
     */
    suspend fun checkForUpdate(context: Context) {
        when (_uiState.value) {
            is UpdateUiState.Downloading, is UpdateUiState.ReadyToInstall -> return
            else -> Unit
        }
        if (hasCheckedThisProcess) return
        hasCheckedThisProcess = true

        val info = fetchLatestRelease() ?: return
        lastInfo = info

        val installedVersionCode = runCatching {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0)
            )
        }.getOrDefault(1L)

        _uiState.value =
            if (isUpdateAvailable(info, installedVersionCode)) {
                UpdateUiState.Available(info)
            } else {
                UpdateUiState.UpToDate
            }
    }

    /** Hides the banner for the rest of the session. */
    fun hide() {
        _uiState.value = UpdateUiState.Idle
    }

    /**
     * Starts downloading the update APK via the system DownloadManager. The download
     * lands in this app's private external files dir, so no storage permission is
     * required and the file is shareable through our FileProvider.
     */
    fun startDownload(context: Context, info: AppUpdateInfo) {
        val appContext = context.applicationContext
        updateDir(appContext).let { dir ->
            // DownloadManager would rename the file if it already exists.
            File(dir, APK_FILE_NAME).delete()
        }
        val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("CloudBox ${info.title}".take(60))
            setDescription("Загрузка обновления CloudBox")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(appContext, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
        }
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        lastEnqueueId = manager.enqueue(request)
        _uiState.value = UpdateUiState.Downloading
    }

    /**
     * Called by MainActivity when DownloadManager broadcasts ACTION_DOWNLOAD_COMPLETE.
     * On success switches to ReadyToInstall and immediately prompts installation.
     */
    fun onDownloadCompleted(context: Context, downloadId: Long) {
        if (downloadId == -1L || downloadId != lastEnqueueId) return
        val appContext = context.applicationContext
        if (queryStatus(appContext, downloadId) == DownloadManager.STATUS_SUCCESSFUL) {
            _uiState.value = UpdateUiState.ReadyToInstall
            tryInstall(appContext)
        } else {
            // Failed (no network, server error, ...) — offer the download again.
            File(updateDir(appContext), APK_FILE_NAME).delete()
            lastInfo?.let { _uiState.value = UpdateUiState.Available(it) }
                ?: run { _uiState.value = UpdateUiState.Idle }
        }
    }

    /**
     * Installs the downloaded APK. If the user has not yet allowed installs from this
     * app, opens the system settings screen and returns false (the banner stays in the
     * ReadyToInstall state so the action can be repeated after granting).
     */
    fun tryInstall(context: Context): Boolean {
        val appContext = context.applicationContext
        val apkFile = File(updateDir(appContext), APK_FILE_NAME)
        if (!apkFile.exists() || apkFile.length() == 0L) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            openUnknownSourcesSettings(appContext)
            return false
        }

        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", apkFile)
        return try {
            val install = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(install)
            true
        } catch (e: ActivityNotFoundException) {
            openReleasePage(appContext)
            false
        }
    }

    /** Opens the rolling release page in a browser (fallback / manual changelog). */
    fun openReleasePage(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(RELEASE_PAGE_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // --- internals -----------------------------------------------------------

    private suspend fun fetchLatestRelease(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(RELEASE_API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "CloudBox-Updater")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val payload = response.body?.string() ?: return@withContext null
                parseRelease(payload)
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun parseRelease(body: String): AppUpdateInfo? {
        return try {
            val json = JSONObject(body)
            if (json.optBoolean("draft", false)) return null

            val title = json.optString("name", "").ifBlank {
                json.optString("tag_name", "CloudBox")
            }
            val releaseBody = json.optString("body", "")
            val publishedIso = json.optString("published_at", "")
            val htmlUrl = json.optString("html_url", RELEASE_PAGE_URL)

            var sha = ""
            var run = -1L
            markerRegex.find(releaseBody)?.let { match ->
                sha = match.groupValues[1]
                run = match.groupValues[2].toLongOrNull() ?: -1L
            }
            if (sha.isEmpty()) {
                titleShaRegex.find(title)?.let { sha = it.groupValues[1] }
            }

            val assets = json.optJSONArray("assets") ?: return null
            val preferredAbis = when {
                Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", true) } ->
                    listOf("arm64-v8a", "universal")
                Build.SUPPORTED_ABIS.any { it.equals("armeabi-v7a", true) } ->
                    listOf("armeabi-v7a", "universal")
                else -> listOf("universal")
            }

            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize = 0L
            outer@ for (abi in preferredAbis) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true) &&
                        name.contains(abi, ignoreCase = true)
                    ) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break@outer
                    }
                }
            }
            if (apkUrl.isNullOrEmpty() || apkName == null) return null

            AppUpdateInfo(
                title = title,
                buildNumber = run,
                commitSha = sha,
                publishedAtMs = parseIsoTime(publishedIso),
                publishedAtIso = publishedIso,
                notes = releaseBody,
                apkUrl = apkUrl,
                apkName = apkName,
                apkSizeBytes = apkSize,
                releasePageUrl = htmlUrl
            )
        } catch (t: Throwable) {
            null
        }
    }

    private fun isUpdateAvailable(info: AppUpdateInfo, installedVersionCode: Long): Boolean {
        // Preferred path: CD writes the CI run number (== APK versionCode) into the release.
        if (info.buildNumber > 0L) return info.buildNumber > installedVersionCode
        // Legacy releases without the marker: compare commit sha and publish date so we
        // never advertise an older release to a newer build.
        val currentSha = BuildConfig.GIT_SHA
        if (currentSha == "dev" || info.commitSha.isEmpty()) return false
        if (info.commitSha.startsWith(currentSha, ignoreCase = true)) return false
        return info.publishedAtMs > BuildConfig.BUILD_TIME - CLOCK_SKEW_MS
    }

    private fun parseIsoTime(iso: String): Long {
        if (iso.isEmpty()) return 0L
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.parse(iso)?.time ?: 0L
        } catch (t: Throwable) {
            0L
        }
    }

    private fun updateDir(context: Context): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "Download").apply { mkdirs() }
    }

    private fun queryStatus(context: Context, downloadId: Long): Int {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                if (index >= 0) return it.getInt(index)
            }
        }
        return -1
    }

    private fun openUnknownSourcesSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { openReleasePage(context) }
    }
}
