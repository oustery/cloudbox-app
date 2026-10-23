package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CloudItem
import com.example.data.repository.ThumbnailSize
import com.example.ui.theme.UiSpacing

@Composable
internal fun VideoErrorPanel(
    errorMessage: String,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xE6000000),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiSpacing.doubleExtraLarge)
            .testTag("video_error_panel")
    ) {
        Column(
            modifier = Modifier.padding(UiSpacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
        ) {
            Text(
                text = "Не удалось воспроизвести видео",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "Возможно, формат файла не поддерживается устройством или соединение прервано ($errorMessage).",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("Повторить")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium)) {
                androidx.compose.material3.OutlinedButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(UiSpacing.iconGap))
                    Text("Скачать", color = Color.White)
                }
                androidx.compose.material3.OutlinedButton(onClick = onOpenExternal) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(UiSpacing.iconGap))
                    Text("Внешний плеер", color = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun MediaMetadataPanel(item: CloudItem, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("media_viewer_metadata_panel"),
        color = Color(0xE6000000),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(horizontal = UiSpacing.large, vertical = UiSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)
        ) {
            Text(
                text = if (item.isVideo) "Видео" else "Фото",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Размер · ${item.formattedSize}",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = item.formattedDate.takeIf { it.isNotBlank() }?.let { "Изменено · $it" }
                    ?: "Дата не указана",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium
            )
            item.formattedDuration?.let { duration ->
                Text(
                    text = "Длительность · $duration",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
internal fun MediaFilmstripPanel(
    items: List<CloudItem>,
    currentItem: CloudItem,
    folderTitle: String,
    viewModel: com.example.ui.viewmodel.CloudLinkViewModel,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Column(modifier = Modifier.padding(horizontal = UiSpacing.extraLarge, vertical = UiSpacing.large)) {
                Text(
                    text = folderTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Фото и видео · ${items.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = UiSpacing.small, vertical = UiSpacing.extraSmall),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)
            ) {
                items(items, key = { it.weblink }) { mediaItem ->
                    val isCurrent = mediaItem.weblink == currentItem.weblink
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                            .selectable(
                                selected = isCurrent,
                                role = androidx.compose.ui.semantics.Role.Button,
                                onClick = { viewModel.selectMediaInPlaylist(mediaItem) }
                            )
                            .padding(UiSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(viewModel.getThumbnailUrl(mediaItem.weblink, ThumbnailSize.SMALL))
                                .crossfade(160)
                                .build(),
                            contentDescription = "Предпросмотр: ${mediaItem.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(UiSpacing.medium))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mediaItem.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (mediaItem.isVideo) "Видео · ${mediaItem.formattedSize}"
                                else "Фото · ${mediaItem.formattedSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

