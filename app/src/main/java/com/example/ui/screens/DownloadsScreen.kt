package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DownloadStatus
import com.example.data.local.DownloadTaskEntity
import com.example.ui.components.ContentStatePanel
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.DownloadViewModel
import java.io.File
import com.example.ui.theme.UiSpacing

@Composable
fun DownloadsScreen(
    viewModel: DownloadViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val activeTab by viewModel.tabFilter.collectAsStateWithLifecycle()
    val tabs = listOf("Все", "В процессе", "Завершено", "С ошибками")

    BackHandler {
        onBack()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.large, vertical = UiSpacing.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Менеджер загрузок",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Показано загрузок: ${downloads.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (downloads.any { it.status == DownloadStatus.COMPLETED.name }) {
                    IconButton(
                        onClick = { viewModel.clearCompleted() },
                        modifier = Modifier.testTag("clear_completed_downloads")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ClearAll,
                            contentDescription = "Очистить завершенные",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Tab Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = UiSpacing.large, vertical = UiSpacing.small),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.small)
        ) {
            tabs.forEach { tab ->
                val isSelected = activeTab == tab
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setFilter(tab) },
                    label = { Text(tab) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("download_tab_$tab")
                )
            }
        }

        // Downloads List or Empty State
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                ContentStatePanel(
                    title = if (activeTab == "Все") "Загрузок пока нет" else "В этом списке пока пусто",
                    message = if (activeTab == "Все")
                        "Файлы, которые вы скачиваете из облака, появятся здесь."
                    else
                        "Когда появятся загрузки в этой категории, они будут показаны здесь.",
                    icon = Icons.Outlined.DownloadDone,
                    actionLabel = if (activeTab != "Все") "Показать все" else null,
                    onAction = if (activeTab != "Все") {
                        { viewModel.setFilter("Все") }
                    } else null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium),
                contentPadding = PaddingValues(horizontal = UiSpacing.large, vertical = UiSpacing.small)
            ) {
                items(downloads, key = { it.id }) { task ->
                    DownloadItemCard(
                        task = task,
                        onPause = { viewModel.pauseDownload(task.id) },
                        onResume = { viewModel.resumeDownload(task.id) },
                        onCancel = { viewModel.cancelDownload(task.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    task: DownloadTaskEntity,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val statusEnum = try {
        DownloadStatus.valueOf(task.status)
    } catch (e: Exception) {
        DownloadStatus.QUEUED
    }
    val statusLabel = when (statusEnum) {
        DownloadStatus.QUEUED -> "В очереди"
        DownloadStatus.DOWNLOADING -> "Скачивается"
        DownloadStatus.PAUSED -> "Приостановлено"
        DownloadStatus.COMPLETED -> "Завершено"
        DownloadStatus.FAILED -> "Ошибка"
    }
    val statusColor = when (statusEnum) {
        DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
        DownloadStatus.COMPLETED -> StatusSuccess
        DownloadStatus.PAUSED -> StatusWarning
        DownloadStatus.FAILED -> StatusError
        DownloadStatus.QUEUED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("download_card_${task.fileName}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when (statusEnum) {
                                DownloadStatus.COMPLETED -> StatusSuccess.copy(alpha = 0.2f)
                                DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primaryContainer
                                DownloadStatus.PAUSED -> StatusWarning.copy(alpha = 0.2f)
                                DownloadStatus.FAILED -> StatusError.copy(alpha = 0.2f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (statusEnum) {
                            DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                            DownloadStatus.DOWNLOADING -> Icons.Default.Download
                            DownloadStatus.PAUSED -> Icons.Default.Pause
                            DownloadStatus.FAILED -> Icons.Default.Error
                            else -> Icons.AutoMirrored.Filled.InsertDriveFile
                        },
                        contentDescription = null,
                        tint = when (statusEnum) {
                            DownloadStatus.COMPLETED -> StatusSuccess
                            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                            DownloadStatus.PAUSED -> StatusWarning
                            DownloadStatus.FAILED -> StatusError
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(UiSpacing.medium))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor
                    )
                    Text(
                        text = when (statusEnum) {
                            DownloadStatus.DOWNLOADING -> "${task.formattedDownloaded} • ${task.formattedSpeed}"
                            DownloadStatus.COMPLETED -> "Файл сохранён • ${task.formattedDownloaded}"
                            DownloadStatus.PAUSED -> task.formattedDownloaded
                            DownloadStatus.FAILED -> task.errorMessage ?: "Загрузку не удалось завершить"
                            DownloadStatus.QUEUED -> "Начнётся автоматически"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Controls
                when (statusEnum) {
                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "Пауза", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Возобновить", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
                        }
                    }
                    DownloadStatus.FAILED -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.Refresh, contentDescription = "Повторить", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Удалить")
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        if (task.destinationPath.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    openDownloadedFile(context, task.destinationPath)
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Открыть", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Удалить из списка")
                        }
                    }
                    else -> {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена")
                        }
                    }
                }
            }

            // Progress bar for active / paused downloads
            if (statusEnum == DownloadStatus.DOWNLOADING || statusEnum == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(UiSpacing.medium))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (statusEnum == DownloadStatus.DOWNLOADING && task.fileSize <= 0L) {
                        LinearProgressIndicator(
                            modifier = Modifier.weight(1f).height(4.dp),
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                        Text(
                            text = "Размер неизвестен",
                            modifier = Modifier.padding(start = UiSpacing.small),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { task.progressPercent },
                            modifier = Modifier.weight(1f).height(4.dp),
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                        Text(
                            text = if (task.fileSize > 0L) task.formattedProgress else "—",
                            modifier = Modifier.padding(start = UiSpacing.small),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun openDownloadedFile(context: android.content.Context, filePath: String) {
    try {
        val file = File(filePath)
        if (!file.exists()) return

        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Uri.fromFile(file)
        }

        val extension = MimeTypeMap.getFileExtensionFromUrl(file.name)
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Открыть файл"))
    } catch (e: Exception) {
        // Fallback or ignore
    }
}
