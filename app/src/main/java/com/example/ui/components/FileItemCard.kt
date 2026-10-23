package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.model.CloudItem
import com.example.ui.components.expressive.MorphingIconButton
import com.example.ui.core.feedback.rememberHaptics
import com.example.ui.theme.UiSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.FileArchive
import com.composables.icons.lucide.FileAudio
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Play

@Composable
fun FileItemCard(
    item: CloudItem,
    thumbnailUrl: String?,
    isGrid: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
    sourceLabel: String? = null,
    modifier: Modifier = Modifier
) {
    if (isGrid) {
        FileGridItem(
            item = item,
            thumbnailUrl = thumbnailUrl,
            isSelected = isSelected,
            isSelectionMode = isSelectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
            onDownloadClick = onDownloadClick,
            sourceLabel = sourceLabel,
            modifier = modifier
        )
    } else {
        FileListItem(
            item = item,
            thumbnailUrl = thumbnailUrl,
            isSelected = isSelected,
            isSelectionMode = isSelectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
            onDownloadClick = onDownloadClick,
            sourceLabel = sourceLabel,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItem(
    item: CloudItem,
    thumbnailUrl: String?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
    sourceLabel: String?,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("file_grid_item_${item.name}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getFileIcon(item),
                    contentDescription = null,
                    tint = getFileIconColor(item),
                    modifier = Modifier.size(44.dp)
                )
            }

            if (thumbnailUrl != null && (item.isImage || item.isVideo)) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(thumbnailUrl)
                        .crossfade(220)
                        .build(),
                    contentDescription = "Предпросмотр: ${item.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    // Shimmer while the preview is downloading; on error the
                    // type icon underneath stays visible as the fallback.
                    loading = {
                        Box(modifier = Modifier.fillMaxSize().shimmerPlaceholder())
                    },
                    error = {}
                )
            }

            // Bottom gradient overlay for legible text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xDC000000))
                        )
                    )
            )

            // Bottom title & size
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = UiSpacing.small, vertical = UiSpacing.iconGap)
            ) {
                Text(
                    text = item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(item.formattedSize, item.formattedDuration)
                        .joinToString(" • "),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            // Top-left type badge or video icon
            if (item.isVideo) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xCC000000),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Play,
                        contentDescription = "Видеофайл",
                        tint = Color.White,
                        modifier = Modifier.padding(UiSpacing.iconGap)
                    )
                }
            }

            // Top-left source badge (unified pool: which saved link this file came from)
            if (sourceLabel != null) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0x96000000),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(UiSpacing.extraSmall)
                        .testTag("file_source_badge")
                ) {
                    Text(
                        text = sourceLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Top-right selection or download badge
            if (isSelectionMode) {
                // Material 3 Expressive: the check morphs circle -> cookie on press.
                MorphingIconButton(
                    imageVector = if (isSelected) Lucide.CircleCheck else Lucide.CircleCheck,
                    contentDescription = if (isSelected) "Снять выделение" else "Выбрать файл",
                    containerColor = if (isSelected) Color.Transparent else Color(0x80000000),
                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                    onClick = onClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(UiSpacing.micro)
                )
            } else if (!item.isFolder) {
                IconButton(
                    onClick = {
                        haptics.tick()
                        onDownloadClick()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(UiSpacing.extraSmall)
                        .background(Color(0x80000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Lucide.Download,
                        contentDescription = "Скачать",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileListItem(
    item: CloudItem,
    thumbnailUrl: String?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownloadClick: () -> Unit,
    sourceLabel: String?,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("file_list_item_${item.name}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon or thumbnail
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getFileIcon(item),
                    contentDescription = null,
                    tint = getFileIconColor(item),
                    modifier = Modifier.size(26.dp)
                )
                if (thumbnailUrl != null && (item.isImage || item.isVideo)) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(thumbnailUrl)
                            .crossfade(180)
                            .build(),
                        contentDescription = "Предпросмотр: ${item.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(modifier = Modifier.fillMaxSize().shimmerPlaceholder())
                        },
                        error = {}
                    )
                }
            }

            Spacer(modifier = Modifier.width(UiSpacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sourceLabel != null) {
                    Spacer(modifier = Modifier.height(UiSpacing.micro))
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(UiSpacing.micro))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    item.formattedDuration?.let { duration ->
                        Text(
                            text = " • $duration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (item.formattedDate.isNotBlank()) {
                        Text(
                            text = " • ${item.formattedDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            if (isSelectionMode) {
                IconButton(
                    onClick = onClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Lucide.CircleCheck else Lucide.CircleCheck,
                        contentDescription = if (isSelected) "Снять выделение" else "Выбрать файл",
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!item.isFolder) {
                IconButton(
                    onClick = {
                        haptics.tick()
                        onDownloadClick()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Download,
                        contentDescription = "Скачать",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

fun getFileIcon(item: CloudItem): ImageVector {
    return when {
        item.isFolder -> Lucide.Folder
        item.isImage -> Lucide.Image
        item.isVideo -> Lucide.Film
        item.isAudio -> Lucide.FileAudio
        item.isArchive -> Lucide.FileArchive
        item.isDocument -> Lucide.FileText
        else -> Lucide.FileText
    }
}

fun getFileIconColor(item: CloudItem): Color {
    return when {
        item.isFolder -> Color(0xFFFFB300)
        item.isImage -> Color(0xFF00D2F7)
        item.isVideo -> Color(0xFFEF4444)
        item.isAudio -> Color(0xFF8B5CF6)
        item.isArchive -> Color(0xFFF97316)
        item.isDocument -> Color(0xFF3B82F6)
        else -> Color(0xFF94A3B8)
    }
}
