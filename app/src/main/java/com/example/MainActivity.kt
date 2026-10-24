package com.example

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.CloudUrlParser
import com.example.data.local.AppDatabase
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.AppSettingsViewModel
import com.example.data.preferences.UiPrefs
import com.example.data.repository.ArchiveRepository
import com.example.data.repository.CloudMailRuRepository
import com.example.data.repository.DownloadRepository
import com.example.data.update.AppUpdateManager
import com.example.ui.navigation.MainAppScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.viewmodel.CloudLinkViewModelFactory
import com.example.ui.viewmodel.DownloadViewModel
import com.example.ui.viewmodel.DownloadViewModelFactory

/**
 * Thin activity shell: wires the process-wide repositories/ViewModels, the
 * update-download receiver and the incoming share/notification intents, then
 * renders [MainAppScreen] — all navigation and UI composition lives there.
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** Extra telling the app which screen to open (used by the download notification). */
        const val EXTRA_OPEN_SCREEN = "open_screen"
        const val SCREEN_DOWNLOADS = "downloads"
    }

    /** Screen requested from outside (notification tap); consumed by [MainAppScreen]. */
    private val pendingScreen = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Denied: downloads keep working, the progress notification is simply hidden.
        }

    /** Delivers DownloadManager completions to the in-app updater. */
    private val updateDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            AppUpdateManager.onDownloadCompleted(applicationContext, downloadId)
        }
    }

    private val cloudViewModel: CloudLinkViewModel by viewModels {
        val db = AppDatabase.getDatabase(applicationContext)
        val repo = CloudMailRuRepository(savedLinkDao = db.savedLinkDao())
        CloudLinkViewModelFactory(
            repository = repo,
            uiPrefs = UiPrefs(applicationContext),
            archiveRepository = ArchiveRepository.getInstance(applicationContext),
            cacheDir = applicationContext.cacheDir
        )
    }

    private val downloadViewModel: DownloadViewModel by viewModels {
        val downloadRepo = DownloadRepository.getInstance(applicationContext)
        DownloadViewModelFactory(downloadRepo)
    }

    private val settingsViewModel: AppSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        ContextCompat.registerReceiver(
            this,
            updateDownloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Handle shared link if launched via Intent.ACTION_SEND
        handleIncomingIntent(intent)

        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }

            MyApplicationTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColors,
                appFont = settings.font
            ) {
                MainAppScreen(
                    cloudViewModel = cloudViewModel,
                    downloadViewModel = downloadViewModel,
                    settings = settings,
                    settingsViewModel = settingsViewModel,
                    pendingScreen = pendingScreen.value,
                    onPendingScreenConsumed = { pendingScreen.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_SCREEN)?.let { requested ->
            pendingScreen.value = requested
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val cloudKey = CloudUrlParser.extractCloudKey(sharedText)
            if (cloudKey != null) {
                cloudViewModel.loadFolder(cloudKey)
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(updateDownloadReceiver) }
        super.onDestroy()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
