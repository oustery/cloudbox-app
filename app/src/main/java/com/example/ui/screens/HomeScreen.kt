package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.CloudUrlParser
import com.example.data.local.SavedLinkEntity
import com.example.data.update.AppUpdateManager
import com.example.data.update.UpdateUiState
import com.example.ui.components.SavedLinkCard
import com.example.ui.components.LinkIntakeCard
import com.example.ui.components.ContentStatePanel
import com.example.ui.components.AdaptiveChipGroup
import com.example.ui.components.UpdateBanner
import com.example.ui.core.feedback.rememberHaptics
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.theme.UiRadius
import com.example.ui.theme.UiSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Link
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.X
import com.example.ui.icons.StarFilled

/** Categories a user can assign to a saved link. */
private val assignableCategories = listOf("Все", "Фото и видео", "Документы", "Архивы")

/** Filter tabs shown above the library list. */
private val homeCategories = listOf("Все", "Избранное", "Фото и видео", "Документы", "Архивы")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: CloudLinkViewModel,
    onOpenFolder: (String) -> Unit,
    onOpenPool: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptics = rememberHaptics()
    val savedLinks by viewModel.savedLinks.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val inputUrl by viewModel.linkInput.collectAsStateWithLifecycle()
    val folderError by viewModel.folderError.collectAsStateWithLifecycle()
    val isRefreshingLinks by viewModel.isRefreshingLinks.collectAsStateWithLifecycle()

    var inputError by remember { mutableStateOf<String?>(null) }
    var renamingLink by remember { mutableStateOf<SavedLinkEntity?>(null) }
    var categorizingLink by remember { mutableStateOf<SavedLinkEntity?>(null) }

    // In-app update check (rolling GitHub release); runs once per process.
    val updateState by AppUpdateManager.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdate(context)
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

    val categories = homeCategories

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = UiSpacing.large)
    ) {
        Spacer(modifier = Modifier.height(UiSpacing.medium))

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
        Spacer(modifier = Modifier.height(UiSpacing.large))

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

        // Unified pool entry: one tap from home to every file of every link.
        PoolEntryCard(
            linkCount = savedLinks.size,
            onClick = onOpenPool
        )
        Spacer(modifier = Modifier.height(UiSpacing.medium))

        // Search in saved links if list has items
        if (savedLinks.isNotEmpty() || searchQuery.isNotBlank()) {
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
                    .fillMaxWidth()
                    .testTag("search_saved_links"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
            Spacer(modifier = Modifier.height(UiSpacing.medium))
        }

        // Category Filter Chips
        AdaptiveChipGroup(items = categories, modifier = Modifier.fillMaxWidth()) { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setCategory(category) },
                    label = { Text(category) },
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
                            "Вставьте публичную ссылку выше и нажмите «Открыть». Ссылка появится здесь.",
                        icon = Lucide.Link,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                    contentPadding = PaddingValues(top = UiSpacing.small, bottom = UiSpacing.extraLarge)
                ) {
                    items(savedLinks, key = { it.id }) { link ->
                        SavedLinkCard(
                            link = link,
                            onClick = { onOpenFolder(link.weblinkKey) },
                            onFavoriteToggle = {
                                haptics.tick()
                                viewModel.toggleFavorite(link.id, link.isFavorite)
                            },
                            onDeleteClick = { viewModel.deleteSavedLinkWithUndo(link) },
                            onRenameClick = { renamingLink = link },
                            onCategoryClick = { categorizingLink = link }
                        )
                    }
                }
            }
        }
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
 * links will be merged so the scope is clear before opening.
 */
@Composable
private fun PoolEntryCard(
    linkCount: Int,
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
                    text = if (linkCount > 0) "Контент со всех ссылок: $linkCount"
                    else "Объединённый просмотр всех ссылок",
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
