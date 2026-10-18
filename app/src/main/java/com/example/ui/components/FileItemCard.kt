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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.CloudItem
import com.example.ui.theme.CloudBlue
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.UiSpacing

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
    modifier: Modifier = Modifier
) {
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
            if (item.isImage && !thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
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
                    text = item.formattedSize,
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
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Видеофайл",
                        tint = Color.White,
                        modifier = Modifier.padding(UiSpacing.iconGap)
                    )
                }
            }

            // Top-right selection or download badge
            if (isSelectionMode) {
                IconButton(
                    onClick = onClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(UiSpacing.micro)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (isSelected) "Снять выделение" else "Выбрать файл",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
            } else if (!item.isFolder) {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(UiSpacing.extraSmall)
                        .background(Color(0x80000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
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
    modifier: Modifier = Modifier
) {
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
                if (item.isImage && !thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = getFileIcon(item),
                        contentDescription = null,
                        tint = getFileIconColor(item),
                        modifier = Modifier.size(26.dp)
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
                Spacer(modifier = Modifier.height(UiSpacing.micro))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = if (isSelected) "Снять выделение" else "Выбрать файл",
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!item.isFolder) {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
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
        item.isFolder -> Icons.Default.Folder
        item.isImage -> Icons.Default.Image
        item.isVideo -> Icons.Default.VideoLibrary
        item.isAudio -> Icons.Default.AudioFile
        item.isArchive -> Icons.Default.FolderZip
        item.isDocument -> Icons.Default.Description
        else -> Icons.Default.Description
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
