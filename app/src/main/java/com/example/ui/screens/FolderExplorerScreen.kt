package com.example.ui.screens

import androidx.activity.compose.BackHandler
// Top-level AnimatedVisibility (inside a Box nested in a Column the ColumnScope
// overload would otherwise win the implicit-receiver resolution).
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibility as OverlayVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CloudItem
import com.example.data.model.CloudItemSortOrder
import com.example.data.model.cloudItemComparator
import com.example.data.repository.ThumbnailSize
import com.example.ui.components.AdaptiveChipGroup
import com.example.ui.components.FileItemCard
import com.example.ui.components.ContentStatePanel
import com.example.ui.components.FolderSkeletonPlaceholder
import com.example.ui.components.expressive.MorphingIconButton
import com.example.ui.viewmodel.AggregatedEntry
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.viewmodel.DownloadViewModel
import com.example.ui.theme.StatusError
import com.example.ui.theme.UiMotionTokens
import com.example.ui.theme.UiSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.FolderX
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.List
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.SquareCheckBig
import com.composables.icons.lucide.X

private val filterChips = listOf("Все", "Медиа", "Фото", "Видео", "Аудио", "Документы", "Архивы")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderExplorerScreen(
    viewModel: CloudLinkViewModel,
    downloadViewModel: DownloadViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingFolder.collectAsStateWithLifecycle()
    val error by viewModel.folderError.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.folderBreadcrumbs.collectAsStateWithLifecycle()

    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val fileFilter by viewModel.fileTypeFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.fileSearchQuery.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()
    val fileSortOrder by viewModel.fileSortOrder.collectAsStateWithLifecycle()
    val gridDensity by viewModel.gridDensity.collectAsStateWithLifecycle()
    val aggregatedEntries by viewModel.aggregatedEntries.collectAsStateWithLifecycle()
    val isLoadingAggregated by viewModel.isLoadingAggregated.collectAsStateWithLifecycle()
    val aggregatedFailedCount by viewModel.aggregatedFailedCount.collectAsStateWithLifecycle()
    val savedLinksTotal by viewModel.savedLinksTotal.collectAsStateWithLifecycle()
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    // Collapsible search: the field is hidden until the magnifier is tapped;
    // collapsing again clears the query so no hidden filter lingers.
    var searchVisible by rememberSaveable { mutableStateOf(false) }

    // Unified pool mode: the explorer shows the merged content of every saved
    // link whenever no specific folder is open.
    val isAggregated = currentFolder == null

    // Auto-load the pool when entering the explorer without an open folder.
    LaunchedEffect(currentFolder) {
        if (viewModel.shouldAutoLoadPool()) {
            viewModel.loadAllContent()
        }
    }

    // Android predictive back handler: selection -> up one level -> pool -> home
    BackHandler {
        when {
            isSelectionMode -> viewModel.clearSelection()
            currentFolder != null -> viewModel.navigateBack()
            else -> onBack()
        }
    }

    // In folder mode entries carry no source; in pool mode each item remembers
    // which saved link it came from.
    val sourceEntries: List<AggregatedEntry> = if (isAggregated) {
        aggregatedEntries
    } else {
        currentFolder?.items?.map { AggregatedEntry(item = it) } ?: emptyList()
    }

    // Chips and their per-chip counters are stable across recompositions; the
    // filter pass itself only reruns when content, filter, query or sort change.
    val chipCounts = remember(sourceEntries) {
        filterChips.associateWith { chip -> sourceEntries.count { it.item.matchesFilter(chip) } }
    }

    val filteredEntries = remember(sourceEntries, fileFilter, searchQuery, fileSortOrder) {
        sourceEntries.filter { entry ->
            val matchesFilter = entry.item.matchesFilter(fileFilter)
            val matchesQuery = if (searchQuery.isBlank()) true else {
                entry.item.name.contains(searchQuery, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }.let { list ->
            val orderComparator = cloudItemComparator(fileSortOrder)
            list.sortedWith(
                compareBy<AggregatedEntry> { !it.item.isFolder }
                    .thenComparator { a, b -> orderComparator.compare(a.item, b.item) }
            )
        }
    }

    // Media playlist reused by grid and list tap handlers; derived from the
    // already-filtered entries so opening the viewer never rebuilds it per click.
    val mediaPlaylist = remember(filteredEntries) {
        filteredEntries.map { it.item }.filter { it.isImage || it.isVideo }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Navigation Bar
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.small, vertical = UiSpacing.iconGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MorphingIconButton(
                        imageVector = Lucide.ArrowLeft,
                        contentDescription = "Назад",
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            when {
                                currentFolder != null -> viewModel.navigateBack()
                                else -> onBack()
                            }
                        },
                        modifier = Modifier.testTag("explorer_back_button")
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAggregated) "Все файлы" else currentFolder?.name ?: "Проводник",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isAggregated) {
                            val distinctSources = aggregatedEntries.mapNotNull { it.sourceWeblinkKey }.distinct().size
                            Text(
                                text = when {
                                    isLoadingAggregated -> "Загрузка содержимого ссылок…"
                                    savedLinksTotal == 0 -> "Нет сохранённых ссылок"
                                    else -> "$distinctSources ссылок • ${aggregatedEntries.size} файлов"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (currentFolder?.items != null) {
                            val countFiles = currentFolder!!.items.count { !it.isFolder }
                            val countFolders = currentFolder!!.items.count { it.isFolder }
                            val totalSize = currentFolder!!.total?.size ?: 0L
                            Text(
                                text = if (totalSize > 0) {
                                    "$countFiles файлов, $countFolders папок • ${formatTotalSize(totalSize)}"
                                } else {
                                    "$countFiles файлов, $countFolders папок"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Collapsible search toggle
                    MorphingIconButton(
                        imageVector = Lucide.Search,
                        contentDescription = "Поиск",
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            if (searchVisible && searchQuery.isNotBlank()) {
                                viewModel.setFileSearchQuery("")
                            }
                            searchVisible = !searchVisible
                        },
                        modifier = Modifier.testTag("explorer_search_toggle")
                    )

                    // Toggle Grid/List
                    MorphingIconButton(
                        imageVector = if (isGridView) Lucide.List else Lucide.LayoutGrid,
                        contentDescription = "Сменить вид",
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = { viewModel.toggleGridView() },
                        modifier = Modifier.testTag("toggle_view_mode")
                    )

                    Box {
                        MorphingIconButton(
                            imageVector = Lucide.ArrowUpDown,
                            contentDescription = "Сортировка: ${fileSortOrder.title}",
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            onClick = { isSortMenuExpanded = true },
                            modifier = Modifier.testTag("explorer_sort_button")
                        )
                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false }
                        ) {
                            CloudItemSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.title) },
                                    onClick = {
                                        viewModel.setFileSortOrder(order)
                                        isSortMenuExpanded = false
                                    },
                                    leadingIcon = if (fileSortOrder == order) {
                                        { Icon(Lucide.Check, contentDescription = null) }
                                    } else null,
                                    modifier = Modifier.testTag("explorer_sort_${order.name}")
                                )
                            }
                        }
                    }

                    // Refresh
                    MorphingIconButton(
                        imageVector = Lucide.RefreshCw,
                        contentDescription = "Обновить",
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            val folder = currentFolder
                            if (folder != null) {
                                viewModel.loadFolder(folder.weblinkKey, resetStack = false)
                            } else {
                                viewModel.loadAllContent()
                            }
                        },
                        modifier = Modifier.testTag("refresh_folder")
                    )
                }

                // Breadcrumbs trail
                if (breadcrumbs.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = UiSpacing.large, vertical = UiSpacing.extraSmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        breadcrumbs.forEachIndexed { index, crumb ->
                            val isLast = index == breadcrumbs.size - 1
                            InputChip(
                                selected = isLast,
                                onClick = {
                                    if (!isLast) viewModel.navigateToBreadcrumb(crumb)
                                },
                                label = { Text(crumb.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.testTag("breadcrumb_${index}")
                            )
                            if (!isLast) {
                                Icon(
                                    imageVector = Lucide.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Search & Filter Row
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.large, vertical = UiSpacing.small)
        ) {
            AnimatedVisibility(
                visible = searchVisible || searchQuery.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setFileSearchQuery(it) },
                    placeholder = {
                        Text(
                            if (isAggregated) "Поиск во всех файлах" else "Поиск в папке",
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = { Icon(Lucide.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setFileSearchQuery("") }) {
                                Icon(Lucide.X, contentDescription = "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explorer_search_field"),
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(UiSpacing.small))

            AdaptiveChipGroup(items = filterChips, modifier = Modifier.fillMaxWidth()) { chip ->
                    val isSelected = fileFilter == chip
                    val count = chipCounts[chip] ?: 0
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFileTypeFilter(chip) },
                        label = { Text("$chip ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.testTag("explorer_filter_$chip")
                    )
            }
        }

        // Partial pool failures note: some links failed, but content is shown
        if (isAggregated && aggregatedFailedCount > 0 && aggregatedEntries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.large, vertical = UiSpacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Не удалось загрузить ссылок: $aggregatedFailedCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusError,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { viewModel.loadAllContent() }) {
                    Text("Повторить")
                }
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val isInitialLoad = !isAggregated && isLoading && currentFolder == null
            val isRefreshingFolder = isLoading && currentFolder != null
            val isRefreshingPool = isAggregated && isLoadingAggregated
            when {
                isInitialLoad -> {
                    // Skeleton placeholders instead of a dead spinner: the
                    // layout is visible before the first byte arrives.
                    FolderSkeletonPlaceholder(
                        isGrid = isGridView,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                isAggregated && isRefreshingPool && aggregatedEntries.isEmpty() -> {
                    // First pool load: skeleton grid while every link is fetched.
                    FolderSkeletonPlaceholder(
                        isGrid = isGridView,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                isAggregated && !isLoadingAggregated && savedLinksTotal == 0 -> {
                    ContentStatePanel(
                        title = "Нет сохранённых ссылок",
                        message = "Откройте публичную ссылку на главном экране — её содержимое появится в общем пуле.",
                        icon = Lucide.Inbox,
                        actionLabel = "На главную",
                        onAction = onBack,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                isAggregated && !isLoadingAggregated && aggregatedEntries.isEmpty() && aggregatedFailedCount > 0 -> {
                    ContentStatePanel(
                        title = "Не удалось загрузить содержимое",
                        message = "Ни одна из сохранённых ссылок не ответила. Проверьте интернет и повторите.",
                        icon = Lucide.CloudOff,
                        actionLabel = "Повторить",
                        onAction = { viewModel.loadAllContent() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                error != null -> {
                    ContentStatePanel(
                        title = "Не удалось открыть папку",
                        message = error.orEmpty(),
                        icon = Lucide.FolderX,
                        actionLabel = "Повторить",
                        onAction = { viewModel.retryLastFolderLoad() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                filteredEntries.isEmpty() -> {
                    val hasActiveFilter = searchQuery.isNotBlank() || fileFilter != "Все"
                    ContentStatePanel(
                        title = if (hasActiveFilter) "Ничего не найдено"
                        else if (isAggregated) "В пуле пока нет файлов"
                        else "Папка пуста",
                        message = if (hasActiveFilter)
                            "Измените запрос или сбросьте выбранные фильтры."
                        else if (isAggregated)
                            "Содержимое сохранённых ссылок появится здесь."
                        else
                            "В этой папке пока нет файлов.",
                        icon = Lucide.FolderOpen,
                        actionLabel = if (hasActiveFilter) "Сбросить фильтры" else null,
                        onAction = if (hasActiveFilter) {
                            {
                                viewModel.setFileSearchQuery("")
                                viewModel.setFileTypeFilter("Все")
                            }
                        } else null,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> {
                    PullToRefreshBox(
                        isRefreshing = if (isAggregated) isRefreshingPool else isRefreshingFolder,
                        onRefresh = {
                            val folder = currentFolder
                            if (folder != null) {
                                viewModel.loadFolder(folder.weblinkKey, resetStack = false)
                            } else {
                                viewModel.loadAllContent()
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isGridView) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = gridDensity.minSizeDp.dp),
                                contentPadding = PaddingValues(UiSpacing.large),
                                horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredEntries, key = { it.item.weblink }) { entry ->
                                    val item = entry.item
                                    val isSelected = selectedItems.contains(item.weblink)

                                    FileItemCard(
                                        item = item,
                                        thumbnailUrl = when {
                                            item.isImage -> viewModel.getThumbnailUrl(item.weblink, ThumbnailSize.HIGH_RES)
                                            item.isVideo -> viewModel.getThumbnailUrl(item.weblink, ThumbnailSize.VIDEO_POSTER)
                                            else -> null
                                        },
                                        isGrid = true,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        sourceLabel = entry.sourceTitle,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleItemSelection(item.weblink)
                                            } else if (item.isFolder) {
                                                if (isAggregated) {
                                                    viewModel.openFromPool(item.weblink)
                                                } else {
                                                    viewModel.loadFolder(item.weblink, resetStack = false)
                                                }
                                            } else if (item.isImage || item.isVideo) {
                                                viewModel.openMediaViewer(
                                                    item,
                                                    playlist = if (isAggregated) mediaPlaylist else null
                                                )
                                            } else {
                                                downloadViewModel.downloadItem(item)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleItemSelection(item.weblink)
                                        },
                                        onDownloadClick = {
                                            downloadViewModel.downloadItem(item)
                                        }
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(UiSpacing.large),
                                verticalArrangement = Arrangement.spacedBy(UiSpacing.small),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredEntries, key = { it.item.weblink }) { entry ->
                                    val item = entry.item
                                    val isSelected = selectedItems.contains(item.weblink)

                                    FileItemCard(
                                        item = item,
                                        thumbnailUrl = item.takeIf { it.isImage || it.isVideo }
                                            ?.let { viewModel.getThumbnailUrl(it.weblink, ThumbnailSize.SMALL) },
                                        isGrid = false,
                                        isSelected = isSelected,
                                        isSelectionMode = isSelectionMode,
                                        sourceLabel = entry.sourceTitle,
                                        onClick = {
                                            if (isSelectionMode) {
                                                viewModel.toggleItemSelection(item.weblink)
                                            } else if (item.isFolder) {
                                                if (isAggregated) {
                                                    viewModel.openFromPool(item.weblink)
                                                } else {
                                                    viewModel.loadFolder(item.weblink, resetStack = false)
                                                }
                                            } else if (item.isImage || item.isVideo) {
                                                viewModel.openMediaViewer(
                                                    item,
                                                    playlist = if (isAggregated) mediaPlaylist else null
                                                )
                                            } else {
                                                downloadViewModel.downloadItem(item)
                                            }
                                        },
                                        onLongClick = {
                                            viewModel.toggleItemSelection(item.weblink)
                                        },
                                        onDownloadClick = {
                                            downloadViewModel.downloadItem(item)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Material 3 Expressive floating selection toolbar (canonical
            // HorizontalFloatingToolbar) with a SplitButton for batch download.
            SelectionToolbarOverlay(
                visible = isSelectionMode,
                selectedCount = selectedItems.size,
                onClose = { viewModel.clearSelection() },
                onSelectAll = { viewModel.selectAllFiles() },
                onClearSelection = { viewModel.clearSelection() },
                onDownloadSelected = {
                    val itemsToDownload = viewModel.getItemsByWeblinks(selectedItems)
                    downloadViewModel.downloadBatch(itemsToDownload)
                    viewModel.clearSelection()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(UiSpacing.extraLarge)
            )
        }
    }
}

/**
 * Material 3 Expressive floating selection toolbar: canonical
 * [HorizontalFloatingToolbar] with a [SplitButtonLayout] for batch download.
 * Extracted into a top-level composable so the top-level AnimatedVisibility
 * overload wins over the ColumnScope one.
 */
@Composable
private fun SelectionToolbarOverlay(
    visible: Boolean,
    selectedCount: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDownloadSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadMenuExpanded = remember { mutableStateOf(false) }
    OverlayVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = UiMotionTokens.shortDurationMillis,
                easing = UiMotionTokens.standardEasing
            )
        ) + slideInVertically(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            initialOffsetY = { fullHeight -> fullHeight / 2 }
        ),
        exit = fadeOut(
            animationSpec = tween(
                durationMillis = UiMotionTokens.shortDurationMillis,
                easing = UiMotionTokens.standardEasing
            )
        ) + slideOutVertically(
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            targetOffsetY = { fullHeight -> fullHeight / 2 }
        ),
        modifier = modifier
    ) {
        HorizontalFloatingToolbar(expanded = true) {
            MorphingIconButton(
                imageVector = Lucide.X,
                contentDescription = "Отмена",
                onClick = onClose
            )

            Text(
                text = "$selectedCount выбрано",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = UiSpacing.small)
            )

            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(onClick = onDownloadSelected) {
                        Icon(Lucide.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(UiSpacing.iconGap))
                        Text("Скачать", fontWeight = FontWeight.Bold)
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TrailingButton(
                        checked = downloadMenuExpanded.value,
                        onCheckedChange = { downloadMenuExpanded.value = it }
                    ) {
                        Icon(Lucide.ChevronDown, contentDescription = "Ещё действия")
                    }
                    DropdownMenu(
                        expanded = downloadMenuExpanded.value,
                        onDismissRequest = { downloadMenuExpanded.value = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Выбрать все") },
                            leadingIcon = { Icon(Lucide.SquareCheckBig, contentDescription = null) },
                            onClick = {
                                onSelectAll()
                                downloadMenuExpanded.value = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Снять выделение") },
                            leadingIcon = { Icon(Lucide.X, contentDescription = null) },
                            onClick = {
                                onClearSelection()
                                downloadMenuExpanded.value = false
                            }
                        )
                    }
                }
            )
        }
    }
}

/** Filter predicate shared by the filter chips (with counters) and the list. */
private fun CloudItem.matchesFilter(filter: String): Boolean = when (filter) {
    "Медиа" -> isImage || isVideo || isAudio
    "Фото" -> isImage
    "Видео" -> isVideo
    "Аудио" -> isAudio
    "Документы" -> isDocument
    "Архивы" -> isArchive
    else -> true
}

/** Human-readable total size for the folder subtitle ("1,2 ГБ"). */
private fun formatTotalSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.getDefault(), "%.1f ГБ", gb)
        mb >= 1.0 -> String.format(java.util.Locale.getDefault(), "%.1f МБ", mb)
        else -> String.format(java.util.Locale.getDefault(), "%.0f КБ", kb)
    }
}
