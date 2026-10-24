package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.preferences.AppSettings
import com.example.ui.core.feedback.HapticsGate
import com.example.ui.core.feedback.UiMessenger
import com.example.ui.core.feedback.rememberHaptics
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.FolderExplorerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BrandGradientEnd
import com.example.ui.theme.BrandGradientStart
import com.example.ui.theme.UiMotionTokens
import com.example.ui.theme.UiSpacing
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.viewmodel.DownloadViewModel

/** Canonical route keys shared by the navigation shell and the screens. */
const val ROUTE_HOME = "home"
const val ROUTE_EXPLORER = "explorer"
const val ROUTE_DOWNLOADS = "downloads"
const val ROUTE_SETTINGS = "settings"

/**
 * The whole app shell: brand top bar on home, bottom navigation / navigation
 * rail (responsive), the current screen and the full-screen media viewer
 * sliding over everything while it is open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    cloudViewModel: CloudLinkViewModel,
    downloadViewModel: DownloadViewModel,
    settings: AppSettings,
    settingsViewModel: com.example.data.preferences.AppSettingsViewModel,
    pendingScreen: String? = null,
    onPendingScreenConsumed: () -> Unit = {}
) {
    // rememberSaveable: screen survives rotation and process recreation;
    // ViewModel-owned state (open folder, filters, selection) survives config
    // changes anyway, so together the whole UI context is restored.
    var currentScreenOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var previousScreen by rememberSaveable { mutableStateOf(ROUTE_HOME) }
    // Cold start lands on the user-configured start screen until the first
    // explicit navigation writes an override (survives rotation via Saveable).
    val currentScreen = currentScreenOverride ?: settings.startScreen.key
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = rememberHaptics()

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
        if (currentScreen != ROUTE_SETTINGS) previousScreen = currentScreen
        currentScreenOverride = ROUTE_SETTINGS
    }
    val activeDownloadsCount by downloadViewModel.activeCount.collectAsStateWithLifecycle()
    val currentFolder by cloudViewModel.currentFolder.collectAsStateWithLifecycle()
    val activeMediaItem by cloudViewModel.activeMediaItem.collectAsStateWithLifecycle()
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    // When folder finishes loading, switch to explorer screen
    LaunchedEffect(currentFolder) {
        if (currentFolder != null && currentScreen == ROUTE_HOME) {
            currentScreenOverride = ROUTE_EXPLORER
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
            // Brand header only on home; the other tabs have their own headers.
            if (activeMediaItem == null && currentScreen == ROUTE_HOME) {
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
                                    painter = painterResource(R.drawable.ic_brand_mark),
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
                                    text = "Mail.ru · Яндекс Диск",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
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
                    navDestinations.forEach { destination ->
                        val selected = currentScreen == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    haptics.tick()
                                    if (destination.route == ROUTE_SETTINGS) openSettings()
                                    else currentScreenOverride = destination.route
                                }
                            },
                            icon = {
                                val icon = if (selected) destination.selectedIcon else destination.unselectedIcon
                                if (destination.route == ROUTE_DOWNLOADS && activeDownloadsCount > 0) {
                                    BadgedBox(
                                        badge = {
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
                                    ) {
                                        NavIcon(icon, destination.label, selected)
                                    }
                                } else {
                                    NavIcon(icon, destination.label, selected)
                                }
                            },
                            label = if (compactBottomNavigation) null else {
                                {
                                    Text(
                                        destination.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            },
                            alwaysShowLabel = !compactBottomNavigation,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.testTag("nav_item_${destination.route}")
                        )
                    }
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
                    navDestinations.forEach { destination ->
                        val selected = currentScreen == destination.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    haptics.tick()
                                    if (destination.route == ROUTE_SETTINGS) openSettings()
                                    else currentScreenOverride = destination.route
                                }
                            },
                            icon = {
                                val icon = if (selected) destination.selectedIcon else destination.unselectedIcon
                                if (destination.route == ROUTE_DOWNLOADS && activeDownloadsCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.tertiary,
                                                contentColor = MaterialTheme.colorScheme.onTertiary
                                            ) {
                                                Text(activeDownloadsCount.toString())
                                            }
                                        }
                                    ) {
                                        NavIcon(icon, destination.label, selected)
                                    }
                                } else {
                                    NavIcon(icon, destination.label, selected)
                                }
                            },
                            label = { Text(destination.label) },
                            alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.testTag("rail_item_${destination.route}")
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (currentScreen) {
                    ROUTE_HOME -> {
                        HomeScreen(
                            viewModel = cloudViewModel,
                            autoUpdateCheck = settings.autoUpdateCheck,
                            onOpenFolder = { weblink ->
                                cloudViewModel.loadFolder(weblink)
                                currentScreenOverride = ROUTE_EXPLORER
                            },
                            onOpenPool = {
                                // Drop any stale open folder; the explorer
                                // auto-loads the unified pool when empty.
                                cloudViewModel.closeFolder()
                                currentScreenOverride = ROUTE_EXPLORER
                            }
                        )
                    }
                    ROUTE_EXPLORER -> {
                        FolderExplorerScreen(
                            viewModel = cloudViewModel,
                            downloadViewModel = downloadViewModel,
                            dataSaver = settings.dataSaver,
                            onBack = { currentScreenOverride = ROUTE_HOME }
                        )
                    }
                    ROUTE_DOWNLOADS -> {
                        DownloadsScreen(
                            viewModel = downloadViewModel,
                            onBack = { currentScreenOverride = ROUTE_HOME }
                        )
                    }
                    ROUTE_SETTINGS -> {
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
