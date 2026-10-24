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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.CloudUrlParser
import com.example.data.local.AppDatabase
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AppSettingsViewModel
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.UiPrefs
import com.example.data.repository.CloudMailRuRepository
import com.example.data.repository.DownloadRepository
import com.example.data.update.AppUpdateManager
import com.example.ui.core.feedback.HapticsGate
import com.example.ui.core.feedback.UiMessenger
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.FolderExplorerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.SettingsScreen
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.example.ui.theme.BrandGradientEnd
import com.example.ui.theme.BrandGradientStart
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.UiMotionTokens
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.viewmodel.CloudLinkViewModelFactory
import com.example.ui.viewmodel.DownloadViewModel
import com.example.ui.viewmodel.DownloadViewModelFactory
import com.example.ui.theme.UiSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CloudDownload
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Settings

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
        CloudLinkViewModelFactory(repo, UiPrefs(applicationContext))
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
                dynamicColor = settings.dynamicColors
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    cloudViewModel: CloudLinkViewModel,
    downloadViewModel: DownloadViewModel,
    settings: AppSettings,
    settingsViewModel: AppSettingsViewModel,
    pendingScreen: String? = null,
    onPendingScreenConsumed: () -> Unit = {}
) {
    // rememberSaveable: screen survives rotation and process recreation;
    // ViewModel-owned state (open folder, filters, selection) survives config
    // changes anyway, so together the whole UI context is restored.
    var currentScreenOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var previousScreen by rememberSaveable { mutableStateOf("home") }
    // Cold start lands on the user-configured start screen until the first
    // explicit navigation writes an override (survives rotation via Saveable).
    val currentScreen = currentScreenOverride ?: settings.startScreen.key
    val snackbarHostState = remember { SnackbarHostState() }

    // Settings-driven global switches applied once per settings change.
    SideEffect {
        HapticsGate.enabled = settings.hapticsEnabled
    }

    // App-level feedback bus -> single SnackbarHost. Undo-style messages carry
    // their action callback inside the UiMessage payload.
    LaunchedEffect(Unit) {
        UiMessenger.messages.collect { message ->
            val hasAction = message.actionLabel != null
            val result = snackbarHostState.showSnackbar(
                message = message.text,
                actionLabel = message.actionLabel,
                duration = if (hasAction) SnackbarDuration.Long else SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                message.onAction?.invoke()
            }
        }
    }
    val openSettings = {
        if (currentScreen != "settings") previousScreen = currentScreen
        currentScreenOverride = "settings"
    }
    val activeDownloadsCount by downloadViewModel.activeCount.collectAsStateWithLifecycle()
    val currentFolder by cloudViewModel.currentFolder.collectAsStateWithLifecycle()
    val activeMediaItem by cloudViewModel.activeMediaItem.collectAsStateWithLifecycle()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // When folder finishes loading, switch to explorer screen
    LaunchedEffect(currentFolder) {
        if (currentFolder != null && currentScreen == "home") {
            currentScreenOverride = "explorer"
        }
    }

    // External navigation request (e.g. tapping the download notification)
    LaunchedEffect(pendingScreen) {
        if (pendingScreen != null) {
            currentScreenOverride = pendingScreen
            onPendingScreenConsumed()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    ) {
        val isWideWindow = maxWidth >= 600.dp
        val compactBottomNavigation = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f

        Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (activeMediaItem == null && currentScreen != "explorer") {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(com.example.R.drawable.ic_brand_mark),
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(UiSpacing.medium))
                            Column {
                                Text(
                                    text = "CloudBox",
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Облако Mail.ru",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    actions = {
                        // Quick Downloads shortcut icon in header
                        IconButton(
                            onClick = { currentScreenOverride = "downloads" },
                            modifier = Modifier.testTag("topbar_downloads_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (activeDownloadsCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = activeDownloadsCount.toString(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Lucide.CloudDownload,
                                    contentDescription = "Загрузки",
                                    tint = if (currentScreen == "downloads") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.testTag("app_top_bar")
                )
            }
        },
        bottomBar = {
            if (!isWideWindow && activeMediaItem == null && !isImeVisible) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .testTag("main_bottom_nav")
                ) {
                    NavigationBarItem(
                        selected = currentScreen == "home",
                        onClick = { currentScreenOverride = "home" },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "home") Lucide.House else Lucide.House,
                                contentDescription = "Главная"
                            )
                        },
                        label = if (compactBottomNavigation) null else {
                            {
                                Text(
                                    "Главная",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        alwaysShowLabel = !compactBottomNavigation,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_home")
                    )

                    NavigationBarItem(
                        selected = currentScreen == "explorer",
                        onClick = { currentScreenOverride = "explorer" },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "explorer") Lucide.Folder else Lucide.Folder,
                                contentDescription = "Проводник"
                            )
                        },
                        label = if (compactBottomNavigation) null else {
                            {
                                Text(
                                    "Проводник",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        alwaysShowLabel = !compactBottomNavigation,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_explorer")
                    )

                    NavigationBarItem(
                        selected = currentScreen == "downloads",
                        onClick = { currentScreenOverride = "downloads" },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeDownloadsCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = activeDownloadsCount.toString(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (currentScreen == "downloads") Lucide.CloudDownload else Lucide.CloudDownload,
                                    contentDescription = "Загрузки"
                                )
                            }
                        },
                        label = if (compactBottomNavigation) null else {
                            {
                                Text(
                                    "Загрузки",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        alwaysShowLabel = !compactBottomNavigation,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_downloads")
                    )
                    NavigationBarItem(
                        selected = currentScreen == "settings",
                        onClick = openSettings,
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "settings") Lucide.Settings else Lucide.Settings,
                                contentDescription = "Настройки"
                            )
                        },
                        label = if (compactBottomNavigation) null else {
                            {
                                Text(
                                    "Настройки",
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        alwaysShowLabel = !compactBottomNavigation,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding()
        ) rowScope@{
            if (isWideWindow && activeMediaItem == null) {
                NavigationRail(
                    modifier = Modifier
                        .fillMaxHeight()
                        .testTag("main_navigation_rail"),
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationRailItem(
                        selected = currentScreen == "home",
                        onClick = { currentScreenOverride = "home" },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "home") Lucide.House else Lucide.House,
                                contentDescription = "Главная"
                            )
                        },
                        label = { Text("Главная") },
                        alwaysShowLabel = true,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("rail_item_home")
                    )
                    NavigationRailItem(
                        selected = currentScreen == "explorer",
                        onClick = { currentScreenOverride = "explorer" },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "explorer") Lucide.Folder else Lucide.Folder,
                                contentDescription = "Проводник"
                            )
                        },
                        label = { Text("Проводник") },
                        alwaysShowLabel = true,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("rail_item_explorer")
                    )
                    NavigationRailItem(
                        selected = currentScreen == "downloads",
                        onClick = { currentScreenOverride = "downloads" },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeDownloadsCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary
                                        ) {
                                            Text(activeDownloadsCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (currentScreen == "downloads") Lucide.CloudDownload else Lucide.CloudDownload,
                                    contentDescription = "Загрузки"
                                )
                            }
                        },
                        label = { Text("Загрузки") },
                        alwaysShowLabel = true,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("rail_item_downloads")
                    )
                    NavigationRailItem(
                        selected = currentScreen == "settings",
                        onClick = openSettings,
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "settings") Lucide.Settings else Lucide.Settings,
                                contentDescription = "Настройки"
                            )
                        },
                        label = { Text("Настройки") },
                        alwaysShowLabel = true,
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("rail_item_settings")
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (currentScreen) {
                    "home" -> {
                        HomeScreen(
                            viewModel = cloudViewModel,
                            autoUpdateCheck = settings.autoUpdateCheck,
                            onOpenFolder = { weblink ->
                                cloudViewModel.loadFolder(weblink)
                                currentScreenOverride = "explorer"
                            },
                            onOpenPool = {
                                // Drop any stale open folder; the explorer
                                // auto-loads the unified pool when empty.
                                cloudViewModel.closeFolder()
                                currentScreenOverride = "explorer"
                            }
                        )
                    }
                    "explorer" -> {
                        FolderExplorerScreen(
                            viewModel = cloudViewModel,
                            downloadViewModel = downloadViewModel,
                            dataSaver = settings.dataSaver,
                            onBack = { currentScreenOverride = "home" }
                        )
                    }
                    "downloads" -> {
                        DownloadsScreen(
                            viewModel = downloadViewModel,
                            onBack = { currentScreenOverride = "home" }
                        )
                    }
                    "settings" -> {
                        SettingsScreen(
                            settingsViewModel = settingsViewModel,
                            onBack = { currentScreenOverride = previousScreen }
                        )
                    }
                }

                this@rowScope.AnimatedVisibility(
                    visible = activeMediaItem != null,
                    enter = slideInVertically(
                        animationSpec = tween(
                            durationMillis = UiMotionTokens.emphasizedDurationMillis,
                            easing = UiMotionTokens.emphasizedEasing
                        ),
                        initialOffsetY = { it }
                    ),
                    exit = slideOutVertically(
                        animationSpec = tween(
                            durationMillis = UiMotionTokens.emphasizedDurationMillis,
                            easing = UiMotionTokens.emphasizedEasing
                        ),
                        targetOffsetY = { it }
                    )
                ) {
                    activeMediaItem?.let { item ->
                        MediaViewerScreen(
                            item = item,
                            viewModel = cloudViewModel,
                            downloadViewModel = downloadViewModel,
                            onClose = { cloudViewModel.closeMediaViewer() }
                        )
                    }
                }
            }
        }
    }
    }
}
