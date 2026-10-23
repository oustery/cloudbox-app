package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.CloudUrlParser
import com.example.data.update.AppUpdateManager
import com.example.data.update.UpdateUiState
import com.example.ui.components.SavedLinkCard
import com.example.ui.components.LinkIntakeCard
import com.example.ui.components.ContentStatePanel
import com.example.ui.components.AdaptiveChipGroup
import com.example.ui.components.UpdateBanner
import com.example.ui.core.feedback.rememberHaptics
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.theme.UiSpacing

@Composable
fun HomeScreen(
    viewModel: CloudLinkViewModel,
    onOpenFolder: (String) -> Unit,
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

    var inputError by remember { mutableStateOf<String?>(null) }

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
        val weblink = CloudUrlParser.extractWeblink(candidate)
        if (weblink == null) {
            inputError = "Неверный формат ссылки Облака Mail.ru"
            return
        }
        inputError = null
        keyboardController?.hide()
        onOpenFolder(weblink)
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

    val categories = listOf("Все", "Избранное", "Фото и видео", "Документы", "Архивы")

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
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить поиск")
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
                                imageVector = Icons.Default.Star,
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

        Spacer(modifier = Modifier.height(UiSpacing.medium))

        // Saved Links List
        if (savedLinks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ContentStatePanel(
                    title = if (searchQuery.isNotBlank()) "Ничего не найдено" else "Нет сохранённых ссылок",
                    message = if (searchQuery.isNotBlank())
                        "Попробуйте изменить запрос или очистить поиск."
                    else
                        "Вставьте публичную ссылку выше и нажмите «Открыть». Ссылка появится здесь.",
                    icon = Icons.Outlined.FolderSpecial,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                contentPadding = PaddingValues(bottom = UiSpacing.extraLarge)
            ) {
                items(savedLinks, key = { it.id }) { link ->
                    SavedLinkCard(
                        link = link,
                        onClick = { onOpenFolder(link.weblinkKey) },
                        onFavoriteToggle = {
                            haptics.tick()
                            viewModel.toggleFavorite(link.id, link.isFavorite)
                        },
                        onDeleteClick = { viewModel.deleteSavedLinkWithUndo(link) }
                    )
                }
            }
        }
    }
}
