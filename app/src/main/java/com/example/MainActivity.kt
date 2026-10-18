package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.CloudUrlParser
import com.example.data.local.AppDatabase
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AppSettingsViewModel
import com.example.data.preferences.AppThemeMode
import com.example.data.repository.CloudMailRuRepository
import com.example.data.repository.DownloadRepository
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.FolderExplorerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CloudBlue
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.UiMotionTokens
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.viewmodel.CloudLinkViewModelFactory
import com.example.ui.viewmodel.DownloadViewModel
import com.example.ui.viewmodel.DownloadViewModelFactory
import com.example.ui.theme.UiSpacing

class MainActivity : ComponentActivity() {

    private val cloudViewModel: CloudLinkViewModel by viewModels {
        val db = AppDatabase.getDatabase(applicationContext)
        val repo = CloudMailRuRepository(savedLinkDao = db.savedLinkDao())
        CloudLinkViewModelFactory(repo)
    }

    private val downloadViewModel: DownloadViewModel by viewModels {
        val db = AppDatabase.getDatabase(applicationContext)
        val cloudRepo = CloudMailRuRepository(savedLinkDao = db.savedLinkDao())
        val downloadRepo = DownloadRepository(applicationContext, db.downloadDao(), cloudRepo)
        DownloadViewModelFactory(downloadRepo)
    }

    private val settingsViewModel: AppSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val weblink = CloudUrlParser.extractWeblink(sharedText)
            if (weblink != null) {
                cloudViewModel.loadFolder(weblink)
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
    settingsViewModel: AppSettingsViewModel
) {
    var currentScreen by remember { mutableStateOf("home") } // home, explorer, downloads, settings
    var previousScreen by remember { mutableStateOf("home") }
    val openSettings = {
        if (currentScreen != "settings") previousScreen = currentScreen
        currentScreen = "settings"
    }
    val activeDownloadsCount by downloadViewModel.activeCount.collectAsStateWithLifecycle()
    val currentFolder by cloudViewModel.currentFolder.collectAsStateWithLifecycle()
    val activeMediaItem by cloudViewModel.activeMediaItem.collectAsStateWithLifecycle()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(settings.gridByDefault) {
        cloudViewModel.setGridView(settings.gridByDefault)
    }

    // When folder finishes loading, switch to explorer screen
    LaunchedEffect(currentFolder) {
        if (currentFolder != null && currentScreen == "home") {
            currentScreen = "explorer"
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    ) {
        val isWideWindow = maxWidth >= 600.dp

        Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                                        Brush.linearGradient(listOf(CloudBlue, CyanAccent))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
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
                                    fontSize = 11.sp,
                                    color = CloudBlue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    actions = {
                        // Quick Downloads shortcut icon in header
                        IconButton(
                            onClick = { currentScreen = "downloads" },
                            modifier = Modifier.testTag("topbar_downloads_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (activeDownloadsCount > 0) {
                                        Badge(
                                            containerColor = CyanAccent,
                                            contentColor = Color.Black
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
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "Загрузки",
                                    tint = if (currentScreen == "downloads") CloudBlue else MaterialTheme.colorScheme.onSurface
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
                        onClick = { currentScreen = "home" },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "home") Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Главная"
                            )
                        },
                        label = { Text("Главная", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_home")
                    )

                    NavigationBarItem(
                        selected = currentScreen == "explorer",
                        onClick = { currentScreen = "explorer" },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "explorer") Icons.Filled.Folder else Icons.Outlined.Folder,
                                contentDescription = "Проводник"
                            )
                        },
                        label = { Text("Проводник", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.testTag("nav_item_explorer")
                    )

                    NavigationBarItem(
                        selected = currentScreen == "downloads",
                        onClick = { currentScreen = "downloads" },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (activeDownloadsCount > 0) {
                                        Badge(
                                            containerColor = CyanAccent,
                                            contentColor = Color.Black
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
                                    imageVector = if (currentScreen == "downloads") Icons.Filled.CloudDownload else Icons.Outlined.CloudDownload,
                                    contentDescription = "Загрузки"
                                )
                            }
                        },
                        label = { Text("Загрузки", fontSize = 11.sp) },
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
                                imageVector = if (currentScreen == "settings") Icons.Filled.Settings else Icons.Outlined.Settings,
                                contentDescription = "Настройки"
                            )
                        },
                        label = { Text("Настройки", fontSize = 11.sp) },
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
                        onClick = { currentScreen = "home" },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "home") Icons.Filled.Home else Icons.Outlined.Home,
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
                        onClick = { currentScreen = "explorer" },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == "explorer") Icons.Filled.Folder else Icons.Outlined.Folder,
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
                        onClick = { currentScreen = "downloads" },
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
                                    imageVector = if (currentScreen == "downloads") Icons.Filled.CloudDownload else Icons.Outlined.CloudDownload,
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
                                imageVector = if (currentScreen == "settings") Icons.Filled.Settings else Icons.Outlined.Settings,
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
                            onOpenFolder = { weblink ->
                                cloudViewModel.loadFolder(weblink)
                                currentScreen = "explorer"
                            }
                        )
                    }
                    "explorer" -> {
                        FolderExplorerScreen(
                            viewModel = cloudViewModel,
                            downloadViewModel = downloadViewModel,
                            onBack = { currentScreen = "home" }
                        )
                    }
                    "downloads" -> {
                        DownloadsScreen(
                            viewModel = downloadViewModel,
                            onBack = { currentScreen = "home" }
                        )
                    }
                    "settings" -> {
                        SettingsScreen(
                            settings = settings,
                            onThemeModeChanged = settingsViewModel::setThemeMode,
                            onDynamicColorsChanged = settingsViewModel::setDynamicColors,
                            onGridByDefaultChanged = settingsViewModel::setGridByDefault,
                            onBack = { currentScreen = previousScreen }
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
