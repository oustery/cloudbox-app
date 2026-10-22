package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTaskEntity
import com.example.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Foreground service that keeps downloads alive while the app is in the
 * background and mirrors their progress into a single notification.
 *
 * The notification aggregates every active/queued transfer and offers
 * "Пауза" / "Отмена" actions. When the queue drains the service posts a short
 * summary (completed vs failed) and stops itself.
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    private lateinit var repository: DownloadRepository
    private lateinit var notificationManager: NotificationManager

    /** Last known status per task id, used to count completed/failed transitions. */
    private val seenStatuses = ConcurrentHashMap<String, String>()
    private var completedCount = 0
    private var failedCount = 0
    private var hadActiveTasks = false

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepository.getInstance(this)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
        startForegroundCompat()
        observeDownloads()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE_ALL -> repository.pauseAll()
            ACTION_CANCEL_ALL -> repository.cancelAll()
        }
        // If the process is killed mid-download the system restarts the service;
        // the repository surfaces interrupted tasks as PAUSED so nothing is lost.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Android 15+ caps dataSync foreground services (~6 h per session). Pause
     * everything so the user can resume with zero lost bytes instead of being
     * killed silently.
     */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(TAG, "Foreground service timeout; pausing downloads")
        repository.pauseAll()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.notification_timeout_title))
            .setContentText(getString(R.string.notification_timeout_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(getString(R.string.notification_timeout_text))
            )
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        notificationManager.notify(NOTIFICATION_ID_SUMMARY, notification)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun observeDownloads() {
        observeJob?.cancel()
        observeJob = serviceScope.launch {
            // Count COMPLETED/FAILED transitions for the drain summary.
            launch {
                repository.allDownloads.collect { tasks ->
                    tasks.forEach { task ->
                        val previous = seenStatuses.put(task.id, task.status)
                        when {
                            task.status == DownloadStatus.COMPLETED.name &&
                                previous != null &&
                                previous != DownloadStatus.COMPLETED.name -> completedCount++

                            task.status == DownloadStatus.FAILED.name &&
                                previous != null &&
                                previous != DownloadStatus.FAILED.name -> failedCount++
                        }
                    }
                }
            }

            repository.activeDownloads.collectLatest { active ->
                if (active.isEmpty()) {
                    if (hadActiveTasks) {
                        // Grace window: a batch enqueued right after this drain
                        // should not tear the service down and repost instantly.
                        delay(STOP_DEBOUNCE_MS)
                        if (repository.activeDownloadsSnapshot().isEmpty()) {
                            postSummaryAndStop()
                        }
                    } else {
                        postSummaryAndStop()
                    }
                } else {
                    hadActiveTasks = true
                    notificationManager.notify(
                        NOTIFICATION_ID_PROGRESS,
                        buildProgressNotification(active)
                    )
                }
            }
        }
    }

    private fun postSummaryAndStop() {
        if (completedCount > 0 || failedCount > 0) {
            val title = when {
                completedCount == 0 && failedCount > 0 ->
                    getString(R.string.notification_summary_title_failed)
                else -> getString(R.string.notification_summary_title)
            }
            val text = buildString {
                append(getString(R.string.notification_summary_completed, completedCount))
                if (failedCount > 0) {
                    append(" • ")
                    append(getString(R.string.notification_summary_failed, failedCount))
                }
            }
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(
                    if (failedCount > 0 && completedCount == 0) {
                        R.drawable.ic_stat_error
                    } else {
                        R.drawable.ic_stat_download
                    }
                )
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .build()
            notificationManager.notify(NOTIFICATION_ID_SUMMARY, notification)
        }

        notificationManager.cancel(NOTIFICATION_ID_PROGRESS)
        hadActiveTasks = false
        completedCount = 0
        failedCount = 0
        seenStatuses.clear()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildProgressNotification(active: List<DownloadTaskEntity>): Notification {
        val activeCount = active.size

        val known = active.filter { it.fileSize > 0L }
        val totalBytes = known.sumOf { it.fileSize }
        val downloadedBytes = known.sumOf { it.downloadedBytes }
        val overallFraction = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }
        val overallPercent = overallFraction?.let { (it * 100).toInt() }

        val speedBps = active.sumOf { it.speedBps.coerceAtLeast(0L) }
        val speedLabel = formatSpeed(speedBps)

        val title = if (activeCount == 1) {
            active.first().fileName
        } else {
            getString(R.string.notification_progress_title_multi, activeCount)
        }
        val text = if (overallPercent != null) {
            getString(R.string.notification_progress_text, overallPercent, speedLabel)
        } else {
            getString(R.string.notification_progress_text_unknown, speedLabel)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (overallFraction != null) {
            builder.setProgress(100, overallPercent ?: 0, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_stat_pause,
                getString(R.string.notification_action_pause),
                serviceActionPendingIntent(ACTION_PAUSE_ALL, REQUEST_PAUSE)
            ).build()
        )
        builder.addAction(
            NotificationCompat.Action.Builder(
                R.drawable.ic_stat_close,
                getString(R.string.notification_action_cancel),
                serviceActionPendingIntent(ACTION_CANCEL_ALL, REQUEST_CANCEL)
            ).build()
        )

        return builder.build()
    }

    private fun startForegroundCompat() {
        val idleNotification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(getString(R.string.notification_idle_title))
            .setContentText(getString(R.string.notification_progress_starting))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID_PROGRESS,
                idleNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID_PROGRESS, idleNotification)
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_SCREEN, MainActivity.SCREEN_DOWNLOADS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun serviceActionPendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, DownloadService::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_channel_downloads_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun formatSpeed(speedBps: Long): String {
        if (speedBps <= 0L) return getString(R.string.notification_speed_unknown)
        val kb = speedBps / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.getDefault(), "%.1f МБ/с", mb)
        } else {
            String.format(Locale.getDefault(), "%.0f КБ/с", kb)
        }
    }

    companion object {
        private const val TAG = "DownloadService"

        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_ID_PROGRESS = 1
        private const val NOTIFICATION_ID_SUMMARY = 2

        private const val ACTION_PAUSE_ALL = "com.example.service.PAUSE_ALL"
        private const val ACTION_CANCEL_ALL = "com.example.service.CANCEL_ALL"

        private const val REQUEST_OPEN_APP = 11
        private const val REQUEST_PAUSE = 12
        private const val REQUEST_CANCEL = 13

        /** Grace period before tearing the service down once the queue drains. */
        private const val STOP_DEBOUNCE_MS = 2_000L

        /**
         * Promotes the download session to a foreground service. Safe to call
         * repeatedly; must only be invoked while the app is in the foreground
         * (all call sites are user actions: enqueue, resume).
         */
        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
