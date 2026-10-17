package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CloudItem
import com.example.ui.components.FileItemCard
import com.example.ui.theme.CloudBlue
import com.example.ui.theme.CyanAccent
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.viewmodel.DownloadViewModel

@Composable
fun FolderExplorerScreen(
    viewModel: CloudLinkViewModel,
    downloadViewModel: DownloadViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingFolder.collectAsStateWithLifecycle()
    val error by viewModel.folderError.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.folderBreadcrumbs.collectAsStateWithLifecycle()

    val isGridView by viewModel.isGridView.collectAsStateWithLifecycle()
    val fileFilter by viewModel.fileTypeFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.fileSearchQuery.collectAsStateWithLifecycle()
    val selectedItems by viewModel.selectedItems.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()

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
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        breadcrumbs.forEachIndexed { index, crumb ->
                            val isLast = index == breadcrumbs.size - 1
                            Text(
                                text = crumb.title,
                                fontSize = 12.sp,
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                color = if (isLast) CloudBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(!isLast) { viewModel.navigateToBreadcrumb(crumb) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
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
        AnimatedVisibility(visible = isSelectionMode) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                            colors = ButtonDefaults.buttonColors(containerColor = CloudBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setFileSearchQuery(it) },
                placeholder = { Text("Поиск файлов в этой папке...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setFileSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explorer_search_field"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CloudBlue,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterChips.forEach { chip ->
                    val isSelected = fileFilter == chip
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.setFileTypeFilter(chip) },
                        label = { Text(chip) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CloudBlue,
                            selectedLabelColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
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
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = CloudBlue)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Загрузка файлов из Облака...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Ошибка доступа",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                currentFolder?.weblinkKey?.let { viewModel.loadFolder(it, resetStack = false) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CloudBlue)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Повторить")
                        }
                    }
                }

                filteredItems.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Файлы не найдены",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                isGridView -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 140.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.weblink }) { item ->
                            val thumbUrl = if (item.isImage) viewModel.getThumbnailUrl(item.weblink, 512) else null
                            val isSelected = selectedItems.contains(item.weblink)

                            FileItemCard(
                                item = item,
                                thumbnailUrl = thumbUrl,
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredItems, key = { it.weblink }) { item ->
                            val thumbUrl = if (item.isImage) viewModel.getThumbnailUrl(item.weblink, 256) else null
                            val isSelected = selectedItems.contains(item.weblink)

                            FileItemCard(
                                item = item,
                                thumbnailUrl = thumbUrl,
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
