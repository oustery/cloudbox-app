package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.local.SavedLinkEntity
import com.example.ui.core.feedback.UiMessenger
import com.example.ui.theme.UiRadius
import com.example.ui.theme.UiSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saved-link card: cover preview from the cloud, human-readable meta line
 * (files count + date added), the raw weblink key and a compact overflow menu
 * (open / copy link / delete) instead of always-visible destructive actions.
 */
@Composable
fun SavedLinkCard(
    link: SavedLinkEntity,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(UiRadius.large))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(UiRadius.large)
            )
            .testTag("saved_link_${link.weblinkKey}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiSpacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover preview or cloud folder fallback
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (link.previewThumbnailUrl != null) {
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(link.previewThumbnailUrl)
                            .crossfade(220)
                            .build(),
                        contentDescription = "Превью: ${link.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        loading = {
                            Box(modifier = Modifier.fillMaxSize().shimmerPlaceholder())
                        },
                        error = {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(UiSpacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(UiSpacing.micro))
                Text(
                    text = buildString {
                        append(if (link.itemCount > 0) filesLabel(link.itemCount) else "Папка")
                        append(" • ")
                        append(formatCardDate(link.createdAt))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(UiSpacing.micro))
                Text(
                    text = link.weblinkKey,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onFavoriteToggle,
                modifier = Modifier.size(40.dp).testTag("saved_link_favorite")
            ) {
                Icon(
                    imageVector = if (link.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Избранное",
                    tint = if (link.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(40.dp).testTag("saved_link_menu")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Ещё действия",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Открыть") },
                        leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        },
                        modifier = Modifier.testTag("saved_link_menu_open")
                    )
                    DropdownMenuItem(
                        text = { Text("Копировать ссылку") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            copyLinkToClipboard(context, link.url)
                        },
                        modifier = Modifier.testTag("saved_link_menu_copy")
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить") },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDeleteClick()
                        },
                        modifier = Modifier.testTag("saved_link_menu_delete")
                    )
                }
            }
        }
    }
}

private fun copyLinkToClipboard(context: Context, url: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("CloudBox link", url))
        UiMessenger.show("Ссылка скопирована")
    } catch (e: Exception) {
        UiMessenger.show("Не удалось скопировать ссылку")
    }
}

/** Russian pluralization: 1 файл / 2 файла / 5 файлов / 12 файлов. */
private fun filesLabel(count: Int): String {
    val abs = count % 100
    val last = abs % 10
    return when {
        abs in 11..14 -> "$count файлов"
        last == 1 -> "$count файл"
        last in 2..4 -> "$count файла"
        else -> "$count файлов"
    }
}

private fun formatCardDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return try {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}
