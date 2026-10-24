package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.CloudUrlParser
import com.example.data.local.SavedLinkEntity
import com.example.data.preferences.LibrarySortOrder
import com.example.data.repository.ArchivePhase
import com.example.data.repository.ArchiveRepository
import com.example.data.update.AppUpdateManager
import com.example.data.update.UpdateUiState
import com.example.ui.components.SavedLinkCard
import com.example.ui.components.ArchiveProgressCard
import com.example.ui.components.LinkIntakeCard
import com.example.ui.components.ContentStatePanel
import com.example.ui.components.AdaptiveChipGroup
import com.example.ui.components.UpdateBanner
import com.example.ui.core.components.ConfirmDialog
import com.example.ui.core.feedback.UiMessenger
import com.example.ui.core.feedback.rememberHaptics
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.theme.UiRadius
import com.example.ui.theme.UiSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.ArrowUpDown
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Files
import com.composables.icons.lucide.FolderDown
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.example.ui.icons.StarFilled
import kotlinx.coroutines.launch

/** Categories a user can assign to a saved link. */
private val assignableCategories = listOf("Все", "Фото и видео", "Документы", "Архивы")

/** Filter tabs shown above the library list. */
private val homeCategories = listOf("Все", "Избранное", "Фото и видео", "Документы", "Архивы")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: CloudLinkViewModel,
    autoUpdateCheck: Boolean = true,
    onOpenFolder: (String) -> Unit,
    onOpenPool: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val savedLinks by viewModel.savedLinks.collectAsStateWithLifecycle()
    val allLinks by viewModel.allLinks.collectAsStateWithLifecycle()
    val homeSortOrder by viewModel.homeSortOrder.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val inputUrl by viewModel.linkInput.collectAsStateWithLifecycle()
    val folderError by viewModel.folderError.collectAsStateWithLifecycle()
    val isRefreshingLinks by viewModel.isRefreshingLinks.collectAsStateWithLifecycle()

    var inputError by remember { mutableStateOf<String?>(null) }
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    var renamingLink by remember { mutableStateOf<SavedLinkEntity?>(null) }
    var categorizingLink by remember { mutableStateOf<SavedLinkEntity?>(null) }
    var isAddSheetOpen by remember { mutableStateOf(false) }
    var isSearchFieldFocused by remember { mutableStateOf(false) }

    // Batch selection for ZIP-archiving (long-press on a card enters it).
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var showArchiveConfirm by remember { mutableStateOf(false) }
    val archiveProgress by viewModel.archiveProgress.collectAsStateWithLifecycle()

    // Live counters per filter chip, derived from the unfiltered library.
    val categoryCounts = remember(allLinks) {
        homeCategories.associateWith { category ->
            when (category) {
                "Все" -> allLinks.size
                "Избранное" -> allLinks.count { it.isFavorite }
                else -> allLinks.count { it.category.equals(category, ignoreCase = true) }
            }
        }
    }

    // In-app update check (rolling GitHub release); runs once per process and
    // only when the user has not turned auto-checks off in settings.
    val updateState by AppUpdateManager.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(autoUpdateCheck) {
        if (autoUpdateCheck) {
            AppUpdateManager.checkForUpdate(context)
        }
    }

    // Archive job result -> one-shot snackbar; then the job leaves the state.
    LaunchedEffect(archiveProgress?.phase) {
        val result = archiveProgress ?: return@LaunchedEffect
        when (result.phase) {
            ArchivePhase.DONE -> {
                val skipped = if (result.filesFailed > 0) " (пропущено: ${result.filesFailed})" else ""
                val truncated = if (result.truncated) " — лимит файлов превышен, архив неполный" else ""
                UiMessenger.show("Архив сохранён в «Загрузки/CloudBox»: ${result.outputFileName}$skipped$truncated")
                viewModel.dismissArchiveResult()
            }
            ArchivePhase.FAILED -> {
                UiMessenger.show(result.errorMessage ?: "Не удалось создать архив")
                viewModel.dismissArchiveResult()
            }
            ArchivePhase.CANCELLED -> {
                UiMessenger.show("Архивация отменена")
                viewModel.dismissArchiveResult()
            }
            else -> {}
        }
    }

    fun submitLink() {
        val candidate = inputUrl.trim()
        if (candidate.isBlank()) {
            inputError = "Введите или вставьте ссылку"
            return
        }
        val cloudKey = CloudUrlParser.extractCloudKey(candidate)
        if (cloudKey == null) {
            inputError = "Поддерживаются ссылки Облака Mail.ru и Яндекс.Диска"
            return
        }
        inputError = null
        isAddSheetOpen = false
        keyboardController?.hide()
        onOpenFolder(cloudKey)
    }

    fun pasteLinkFromClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val text = clipData.getItemAt(0).text?.toString() ?: ""
            if (text.isNotBlank()) {
                viewModel.setLinkInput(text)
            }
        }
    }

    fun toggleLinkSelection(id: Long) {
        val current = selectedIds
        selectedIds = if (current.contains(id)) {
            current.filterNot { it == id }.toLongArray()
        } else {
            current + id
        }
        if (selectedIds.isEmpty()) selectionMode = false
    }

    fun enterSelectionMode(id: Long) {
        haptics.tick()
        selectionMode = true
        if (!selectedIds.contains(id)) selectedIds = selectedIds + id
    }

    fun selectAllLinks() {
        haptics.tick()
        selectedIds = allLinks.map { it.id }.toLongArray()
        selectionMode = true
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIds = longArrayOf()
    }

    val listState = rememberLazyListState()

    // Flexible chrome: the search + filters bar collapses as the user dives
    // into the library and returns at the top or when search is active.
    val atTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 48
        }
    }
    val controlsVisible = homeControlsVisible(
        atTop = atTop,
        searchQuery = searchQuery,
        searchFieldFocused = isSearchFieldFocused,
        hasContent = allLinks.isNotEmpty() || searchQuery.isNotBlank()
    )
    val showScrollToTop by remember {
        derivedStateOf { showScrollToTop(listState.firstVisibleItemIndex) }
    }
    // FABs live under the keyboard otherwise, mirroring the bottom bar policy.
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val categories = homeCategories

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = UiSpacing.large)
        ) {
            Spacer(modifier = Modifier.height(UiSpacing.medium))

            AnimatedVisibility(
                visible = updateState is UpdateUiState.Available ||
                    updateState is UpdateUiState.Downloading ||
                    updateState is UpdateUiState.ReadyToInstall,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                UpdateBanner(
                    state = updateState,
                    onDownload = {
                        (updateState as? UpdateUiState.Available)?.let { available ->
                            AppUpdateManager.startDownload(context, available.info)
                        }
                    },
                    onInstall = { AppUpdateManager.tryInstall(context) },
                    onOpenRelease = { AppUpdateManager.openReleasePage(context) },
                    onDismiss = { AppUpdateManager.hide() }
                )
                Spacer(modifier = Modifier.height(UiSpacing.large))
            }

            if (selectionMode) {
                SelectionBar(
                    count = selectedIds.size,
                    onSelectAll = ::selectAllLinks,
                    onClose = ::exitSelectionMode
                )
                Spacer(modifier = Modifier.height(UiSpacing.medium))
            } else {
                // Library at a glance: links, files, favorites.
                LibraryStatsRow(
                    linkCount = allLinks.size,
                    fileCount = allLinks.sumOf { it.itemCount },
                    favoriteCount = allLinks.count { it.isFavorite },
                    onFavoritesClick = {
                        haptics.tick()
                        viewModel.setCategory("Избранное")
                    }
                )
                Spacer(modifier = Modifier.height(UiSpacing.medium))

                // Unified pool entry: one tap from home to every file of every link.
                PoolEntryCard(
                    linkCount = allLinks.size,
                    fileCount = allLinks.sumOf { it.itemCount },
                    onClick = onOpenPool
                )
                Spacer(modifier = Modifier.height(UiSpacing.medium))
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    // Search + library sort.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = {
                                Text(
                                    "Поиск сохранённых ссылок",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Lucide.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Lucide.X, contentDescription = "Очистить поиск")
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("search_saved_links")
                                .onFocusChanged { isSearchFieldFocused = it.isFocused },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )
                        Box {
                            IconButton(
                                onClick = { isSortMenuExpanded = true },
                                modifier = Modifier.testTag("home_sort_button")
                            ) {
                                Icon(
                                    imageVector = Lucide.ArrowUpDown,
                                    contentDescription = "Сортировка библиотеки",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = isSortMenuExpanded,
                                onDismissRequest = { isSortMenuExpanded = false }
                            ) {
                                LibrarySortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.title) },
                                        onClick = {
                                            haptics.tick()
                                            viewModel.setHomeSortOrder(order)
                                            isSortMenuExpanded = false
                                        },
                                        trailingIcon = if (homeSortOrder == order) {
                                            {
                                                Icon(
                                                    imageVector = Lucide.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(UiSpacing.medium))

                    // Category Filter Chips with live counts
                    AdaptiveChipGroup(items = categories, modifier = Modifier.fillMaxWidth()) { category ->
                        val isSelected = selectedCategory == category
                        val count = categoryCounts[category] ?: 0
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setCategory(category) },
                            label = { Text(if (count > 0) "$category · $count" else category) },
                            leadingIcon = if (category == "Избранное" && isSelected) {
                                {
                                    Icon(
                                        imageVector = StarFilled,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.testTag("category_chip_$category")
                        )
                    }
                    Spacer(modifier = Modifier.height(UiSpacing.small))
                }
            }

            // Saved Links List — pull down to refresh counts and covers
            PullToRefreshBox(
                isRefreshing = isRefreshingLinks,
                onRefresh = { viewModel.refreshSavedLinkCounts() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (savedLinks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ContentStatePanel(
                            title = if (searchQuery.isNotBlank()) "Ничего не найдено" else "Нет сохранённых ссылок",
                            message = if (searchQuery.isNotBlank())
                                "Попробуйте изменить запрос или очистить поиск."
                            else
                                "Нажмите «Добавить» в правом нижнем углу, чтобы сохранить первую публичную ссылку.",
                            icon = Lucide.Link,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                        contentPadding = PaddingValues(top = UiSpacing.small, bottom = UiSpacing.extraLarge)
                    ) {
                        items(savedLinks, key = { it.id }) { link ->
                            SavedLinkCard(
                                link = link,
                                onClick = {
                                    if (selectionMode) toggleLinkSelection(link.id)
                                    else onOpenFolder(link.weblinkKey)
                                },
                                onFavoriteToggle = {
                                    haptics.tick()
                                    viewModel.toggleFavorite(link.id, link.isFavorite)
                                },
                                onDeleteClick = { viewModel.deleteSavedLinkWithUndo(link) },
                                onRenameClick = { renamingLink = link },
                                onCategoryClick = { categorizingLink = link },
                                isSelectionMode = selectionMode,
                                isSelected = selectedIds.contains(link.id),
                                onLongPress = { enterSelectionMode(link.id) }
                            )
                        }
                    }
                }
            }
        }

        // Floating actions: scroll-to-top shortcut above the add-link entry.
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = UiSpacing.large, bottom = UiSpacing.extraLarge)
        ) {
            AnimatedVisibility(
                visible = showScrollToTop && !isImeVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        haptics.tick()
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("scroll_to_top_button")
                ) {
                    Icon(
                        imageVector = Lucide.ArrowUp,
                        contentDescription = "Наверх"
                    )
                }
            }
            Spacer(modifier = Modifier.height(UiSpacing.small))
            AnimatedVisibility(
                visible = !isImeVisible,
                enter = fadeIn() + scaleIn(initialScale = 0.6f),
                exit = fadeOut() + scaleOut(targetScale = 0.6f)
            ) {
                if (selectionMode) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptics.tick()
                            if (selectedIds.isNotEmpty()) showArchiveConfirm = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("archive_fab")
                    ) {
                        Icon(
                            imageVector = Lucide.FolderDown,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(UiSpacing.extraSmall))
                        Text(
                            text = "Архив (${selectedIds.size})",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    ExtendedFloatingActionButton(
                        onClick = {
                            haptics.tick()
                            isAddSheetOpen = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("add_link_fab")
                    ) {
                        Icon(
                            imageVector = Lucide.Plus,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(UiSpacing.extraSmall))
                        Text(
                            text = "Добавить",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ZIP-archive progress floats above the FAB stack (left-bottom side).
        ArchiveProgressCard(
            progress = archiveProgress,
            onCancel = { viewModel.cancelArchive() },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = UiSpacing.large)
                .padding(bottom = 96.dp)
        )
    }

    // Add-link modal — the intake form lives here instead of hogging the top
    // of the library list.
    if (isAddSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { isAddSheetOpen = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = UiSpacing.large)
                    .padding(bottom = UiSpacing.extraLarge)
            ) {
                Text(
                    text = "Добавить ссылку",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(UiSpacing.large))
                LinkIntakeCard(
                    value = inputUrl,
                    onValueChange = {
                        viewModel.setLinkInput(it)
                        inputError = null
                    },
                    onPaste = ::pasteLinkFromClipboard,
                    onSubmit = ::submitLink,
                    errorMessage = inputError ?: folderError
                )
            }
        }
    }

    // Archive confirmation: warns about the hard caps before the long job.
    if (showArchiveConfirm) {
        ConfirmDialog(
            title = "Скачать в ZIP-архив?",
            text = "Содержимое ${selectedIds.size} ${linksPlural(selectedIds.size)} будет упаковано в один архив " +
                "(лимиты: ${ArchiveRepository.MAX_FILES} файлов, 3 ГБ). " +
                "Держите приложение открытым до завершения — архивация не идёт в фоне.",
            confirmLabel = "Создать архив",
            onConfirm = {
                showArchiveConfirm = false
                val links = allLinks.filter { selectedIds.contains(it.id) }
                viewModel.archiveLinks(links)
                exitSelectionMode()
            },
            onDismiss = { showArchiveConfirm = false }
        )
    }

    // Rename dialog
    renamingLink?.let { link ->
        RenameLinkDialog(
            initialTitle = link.title,
            onConfirm = { newTitle ->
                viewModel.renameSavedLink(link.id, newTitle)
                renamingLink = null
            },
            onDismiss = { renamingLink = null }
        )
    }

    // Category dialog
    categorizingLink?.let { link ->
        CategoryPickerDialog(
            currentCategory = link.category,
            onPick = { category ->
                haptics.tick()
                viewModel.setSavedLinkCategory(link.id, category)
                categorizingLink = null
            },
            onDismiss = { categorizingLink = null }
        )
    }
}

/**
 * One-tap entry into the unified content pool ("Все файлы"): shows how many
 * links and files will be merged so the scope is clear before opening.
 */
@Composable
private fun PoolEntryCard(
    linkCount: Int,
    fileCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.large))
            .clickable(onClick = onClick)
            .testTag("pool_entry_card"),
        shape = RoundedCornerShape(UiRadius.large),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.large, vertical = UiSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Lucide.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(UiSpacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Все файлы",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = when {
                        linkCount <= 0 -> "Объединённый просмотр всех ссылок"
                        else -> "Ссылок: $linkCount · Файлов: ${formatCountCompact(fileCount)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Lucide.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Summary tiles above the library: total links, total files across all of
 * them and the favorites shortcut (taps into the "Избранное" filter).
 */
@Composable
private fun LibraryStatsRow(
    linkCount: Int,
    fileCount: Int,
    favoriteCount: Int,
    onFavoritesClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
    ) {
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Lucide.Link2,
            value = linkCount.toString(),
            label = "Ссылки"
        )
        StatTile(
            modifier = Modifier.weight(1f),
            icon = Lucide.Files,
            value = formatCountCompact(fileCount),
            label = "Файлов"
        )
        StatTile(
            modifier = Modifier.weight(1f),
            icon = StarFilled,
            value = favoriteCount.toString(),
            label = "Избранное",
            onClick = onFavoritesClick,
            accent = favoriteCount > 0
        )
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Boolean = false
) {
    val tileColor = if (accent) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(UiRadius.large))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag("stat_tile_$label"),
        shape = RoundedCornerShape(UiRadius.large),
        color = tileColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.medium, vertical = UiSpacing.small)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (accent) MaterialTheme.colorScheme.onTertiaryContainer
                else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(UiSpacing.extraSmall))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Keeps large numbers compact inside the small tiles. */
private fun formatCountCompact(n: Int): String =
    if (n >= 10_000) "%.1f тыс.".format(n / 1000f) else n.toString()

/** Russian pluralization for links: 1 ссылка / 2 ссылки / 5 ссылок. */
internal fun linksPlural(count: Int): String {
    val abs = count % 100
    val last = abs % 10
    return when {
        abs in 11..14 -> "$count ссылок"
        last == 1 -> "$count ссылка"
        last in 2..4 -> "$count ссылки"
        else -> "$count ссылок"
    }
}

/**
 * Selection-mode header: replaces the stats/pool block while the user picks
 * links for a batch operation. Shows the count, select-all and the exit.
 */
@Composable
private fun SelectionBar(
    count: Int,
    onSelectAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("selection_bar"),
        shape = RoundedCornerShape(UiRadius.large),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = UiSpacing.extraSmall, end = UiSpacing.small, top = UiSpacing.extraSmall, bottom = UiSpacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.testTag("selection_close")) {
                Icon(
                    imageVector = Lucide.X,
                    contentDescription = "Выйти из выделения",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Text(
                text = "Выбрано: $count",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSelectAll, enabled = count > 0) {
                Text(
                    "Выбрать всё",
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun RenameLinkDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переименовать ссылку") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_link_field")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
                modifier = Modifier.testTag("rename_link_confirm")
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun CategoryPickerDialog(
    currentCategory: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Категория ссылки") },
        text = {
            Column {
                assignableCategories.forEach { category ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(category) }
                            .testTag("category_option_$category")
                    ) {
                        RadioButton(
                            selected = category == currentCategory,
                            onClick = { onPick(category) }
                        )
                        Text(
                            text = category,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
