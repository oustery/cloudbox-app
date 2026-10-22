package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.data.model.sortedBy
import com.example.data.repository.ThumbnailSize
import com.example.ui.components.AdaptiveChipGroup
import com.example.ui.components.FileItemCard
import com.example.ui.components.ContentStatePanel
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.viewmodel.DownloadViewModel
import com.example.ui.theme.UiMotionTokens
import com.example.ui.theme.UiSpacing

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
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    // Android predictive back handler
    BackHandler {
        if (isSelectionMode) {
            viewModel.clearSelection()
        } else if (!viewModel.navigateBack()) {
            onBack()
        }
    }

    val filterChips = listOf("Все", "Фото", "Видео", "Документы", "Архивы")

    val filteredItems = (currentFolder?.items ?: emptyList()).filter { item ->
        val matchesFilter = when (fileFilter) {
            "Фото" -> item.isImage
            "Видео" -> item.isVideo
            "Документы" -> item.isDocument
            "Архивы" -> item.isArchive
            else -> true
        }
        val matchesQuery = if (searchQuery.isBlank()) true else {
            item.name.contains(searchQuery, ignoreCase = true)
        }
        matchesFilter && matchesQuery
    }.sortedBy(fileSortOrder)

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
                    IconButton(
                        onClick = {
                            if (!viewModel.navigateBack()) {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("explorer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentFolder?.name ?: "Проводник",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentFolder?.items != null) {
                            val countFiles = currentFolder!!.items.count { !it.isFolder }
                            val countFolders = currentFolder!!.items.count { it.isFolder }
                            Text(
                                text = "$countFiles файлов, $countFolders папок",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Toggle Grid/List
                    IconButton(
                        onClick = { viewModel.toggleGridView() },
                        modifier = Modifier.testTag("toggle_view_mode")
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            contentDescription = "Сменить вид",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { isSortMenuExpanded = true },
                            modifier = Modifier.testTag("explorer_sort_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Сортировка: ${fileSortOrder.title}",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null,
                                    modifier = Modifier.testTag("explorer_sort_${order.name}")
                                )
                            }
                        }
                    }

                    // Refresh
                    IconButton(
                        onClick = {
                            currentFolder?.weblinkKey?.let { viewModel.loadFolder(it, resetStack = false) }
                        },
                        modifier = Modifier.testTag("refresh_folder")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Обновить",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
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
                                    imageVector = Icons.Default.ChevronRight,
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

        // Selection Action Bar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = UiMotionTokens.shortDurationMillis,
                    easing = UiMotionTokens.standardEasing
                )
            ),
            exit = fadeOut(
                animationSpec = tween(
                    durationMillis = UiMotionTokens.shortDurationMillis,
                    easing = UiMotionTokens.standardEasing
                )
            )
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = UiSpacing.large, vertical = UiSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
                        }
                        Text(
                            text = "Выбрано: ${selectedItems.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Row {
                        IconButton(onClick = { viewModel.selectAllFiles() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Выбрать все")
                        }
                        Button(
                            onClick = {
                                val itemsToDownload = (currentFolder?.items ?: emptyList()).filter {
                                    selectedItems.contains(it.weblink) && !it.isFolder
                                }
                                downloadViewModel.downloadBatch(itemsToDownload)
                                viewModel.clearSelection()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(UiSpacing.iconGap))
                            Text("Скачать", fontWeight = FontWeight.Bold)
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setFileSearchQuery(it) },
                placeholder = {
                    Text(
                        "Поиск в папке",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setFileSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explorer_search_field"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            )

            Spacer(modifier = Modifier.height(UiSpacing.small))

            AdaptiveChipGroup(items = filterChips, modifier = Modifier.fillMaxWidth()) { chip ->
                    val isSelected = fileFilter == chip
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFileTypeFilter(chip) },
                        label = { Text(chip) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("explorer_filter_$chip")
                    )
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                isLoading -> {
                    ContentStatePanel(
                        title = "Загружаем файлы",
                        message = "Получаем содержимое папки из облака.",
                        isLoading = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                error != null -> {
                    ContentStatePanel(
                        title = "Не удалось открыть папку",
                        message = error.orEmpty(),
                        icon = Icons.Outlined.FolderOff,
                        actionLabel = "Повторить",
                        onAction = { viewModel.retryLastFolderLoad() },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                filteredItems.isEmpty() -> {
                    val hasActiveFilter = searchQuery.isNotBlank() || fileFilter != "Все"
                    ContentStatePanel(
                        title = if (hasActiveFilter) "Ничего не найдено" else "Папка пуста",
                        message = if (hasActiveFilter)
                            "Измените запрос или сбросьте выбранные фильтры."
                        else
                            "В этой папке пока нет файлов.",
                        icon = Icons.Outlined.FolderOff,
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

                isGridView -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        contentPadding = PaddingValues(UiSpacing.large),
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.weblink }) { item ->
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
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleItemSelection(item.weblink)
                                    } else if (item.isFolder) {
                                        viewModel.loadFolder(item.weblink, resetStack = false)
                                    } else if (item.isImage || item.isVideo) {
                                        viewModel.openMediaViewer(item)
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

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(UiSpacing.large),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.small),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.weblink }) { item ->
                            val isSelected = selectedItems.contains(item.weblink)

                            FileItemCard(
                                item = item,
                                thumbnailUrl = item.takeIf { it.isImage || it.isVideo }
                                    ?.let { viewModel.getThumbnailUrl(it.weblink, ThumbnailSize.SMALL) },
                                isGrid = false,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onClick = {
                                    if (isSelectionMode) {
                                        viewModel.toggleItemSelection(item.weblink)
                                    } else if (item.isFolder) {
                                        viewModel.loadFolder(item.weblink, resetStack = false)
                                    } else if (item.isImage || item.isVideo) {
                                        viewModel.openMediaViewer(item)
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
}
