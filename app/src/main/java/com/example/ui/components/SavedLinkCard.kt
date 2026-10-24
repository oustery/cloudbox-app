package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.api.LinkProvider
import com.example.data.local.SavedLinkEntity
import com.example.ui.core.feedback.UiMessenger
import com.example.ui.theme.UiRadius
import com.example.ui.theme.UiSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Circle
import com.composables.icons.lucide.CircleCheckBig
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Folder
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Tags
import com.composables.icons.lucide.Trash2
import com.example.ui.icons.StarFilled

/**
 * Saved-link card with reversible swipe actions:
 *  - swipe right -> favorite toggle (springs back, does not dismiss)
 *  - swipe left  -> delete (goes through the undo snackbar path)
 * Plus the overflow menu: open / rename / category / copy link / delete.
 *
 * Selection mode (batch operations): long-press enters it; while active the
 * card toggles the checkbox on tap, hides the swipe gestures and the menu,
 * and draws a highlighted border when selected.
 */
@Composable
fun SavedLinkCard(
    link: SavedLinkEntity,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDeleteClick: () -> Unit,
    onRenameClick: () -> Unit,
    onCategoryClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onLongPress: () -> Unit = {}
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    // Swipe right (StartToEnd) = favorite: the change fires, then the card
    // springs back because confirmValueChange returns false. Swipe left
    // (EndToStart) = delete: settles, the list drops the item, undo restores.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onFavoriteToggle()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDeleteClick()
                    true
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.testTag("saved_link_swipe_${link.weblinkKey}"),
        // In selection mode the horizontal swipe must not fight the tap-to-toggle.
        gesturesEnabled = !isSelectionMode,
        backgroundContent = {
            val isDeleteSide = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val backgroundColor =
                if (isDeleteSide) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.tertiaryContainer
            val contentColor =
                if (isDeleteSide) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onTertiaryContainer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(UiRadius.large))
                    .background(backgroundColor)
                    .padding(horizontal = UiSpacing.extraLarge),
                contentAlignment = if (isDeleteSide) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                if (isDeleteSide) {
                    Icon(
                        imageVector = Lucide.Trash2,
                        contentDescription = "Удалить свайпом",
                        tint = contentColor
                    )
                } else {
                    Icon(
                        imageVector = StarFilled,
                        contentDescription = "В избранное свайпом",
                        tint = contentColor
                    )
                }
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(UiRadius.large))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongPress
                )
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(UiRadius.large)
                )
                .testTag("saved_link_${link.weblinkKey}"),
            colors = CardDefaults.cardColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
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
                                    imageVector = Lucide.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        )
                    } else {
                        Icon(
                            imageVector = Lucide.Folder,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProviderBadge(provider = LinkProvider.detectOrDefault(link.weblinkKey))
                        Spacer(modifier = Modifier.width(UiSpacing.small))
                        if (link.category.isNotBlank() && link.category != "Все") {
                            CategoryTag(label = link.category)
                            Spacer(modifier = Modifier.width(UiSpacing.small))
                        }
                        Text(
                            text = link.weblinkKey,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isSelectionMode) {
                    // Checkbox instead of the favorite star while selecting.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("saved_link_select_${link.weblinkKey}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Lucide.CircleCheckBig,
                                contentDescription = "Выбрано",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Lucide.Circle,
                                contentDescription = "Не выбрано",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onFavoriteToggle,
                        modifier = Modifier.size(40.dp).testTag("saved_link_favorite")
                    ) {
                        Icon(
                            imageVector = if (link.isFavorite) StarFilled else Lucide.Star,
                            contentDescription = "Избранное",
                            tint = if (link.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isSelectionMode) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(40.dp).testTag("saved_link_menu")
                        ) {
                            Icon(
                                imageVector = Lucide.EllipsisVertical,
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
                            leadingIcon = { Icon(Lucide.ExternalLink, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            },
                            modifier = Modifier.testTag("saved_link_menu_open")
                        )
                        DropdownMenuItem(
                            text = { Text("Переименовать") },
                            leadingIcon = { Icon(Lucide.Pencil, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRenameClick()
                            },
                            modifier = Modifier.testTag("saved_link_menu_rename")
                        )
                        DropdownMenuItem(
                            text = { Text("Категория") },
                            leadingIcon = { Icon(Lucide.Tags, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onCategoryClick()
                            },
                            modifier = Modifier.testTag("saved_link_menu_category")
                        )
                        DropdownMenuItem(
                            text = { Text("Копировать ссылку") },
                            leadingIcon = { Icon(Lucide.Copy, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                copyLinkToClipboard(context, link.url)
                            },
                            modifier = Modifier.testTag("saved_link_menu_copy")
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Удалить",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Lucide.Trash2,
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
    }
}

/** Small colored tag showing the link's library category. */
@Composable
private fun CategoryTag(label: String) {
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = UiSpacing.iconGap, vertical = UiSpacing.micro)
    )
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
internal fun filesLabel(count: Int): String {
    val abs = count % 100
    val last = abs % 10
    return when {
        abs in 11..14 -> "$count файлов"
        last == 1 -> "$count файл"
        last in 2..4 -> "$count файла"
        else -> "$count файлов"
    }
}

// Shared formatter: SimpleDateFormat construction is expensive and this runs for
// every card on every recomposition; UI calls are main-thread only, so one
// instance is safe to reuse.
private val cardDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

private fun formatCardDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return try {
        cardDateFormat.format(Date(timestamp))
    } catch (e: Exception) {
        ""
    }
}

/**
 * Compact provider chip ("М" / "Я") so the user always sees which cloud a
 * saved link belongs to. Theme tokens keep it consistent in both color schemes.
 */
@Composable
private fun ProviderBadge(provider: LinkProvider, modifier: Modifier = Modifier) {
    val containerColor = when (provider) {
        LinkProvider.YANDEX -> MaterialTheme.colorScheme.tertiaryContainer
        LinkProvider.MEGA -> MaterialTheme.colorScheme.secondaryContainer
        LinkProvider.MAILRU -> MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = when (provider) {
        LinkProvider.YANDEX -> MaterialTheme.colorScheme.onTertiaryContainer
        LinkProvider.MEGA -> MaterialTheme.colorScheme.onSecondaryContainer
        LinkProvider.MAILRU -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = provider.shortLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}
