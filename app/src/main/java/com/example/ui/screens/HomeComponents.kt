package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Files
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Link2
import com.composables.icons.lucide.X
import com.example.data.local.SavedLinkEntity
import com.example.ui.icons.StarFilled
import com.example.ui.theme.UiRadius
import com.example.ui.theme.UiSpacing

/** Categories a user can assign to a saved link (category dialog). */
private val assignableCategories = listOf("Все", "Фото и видео", "Документы", "Архивы")

/**
 * One-tap entry into the unified content pool ("Все файлы"): shows how many
 * links and files will be merged so the scope is clear before opening.
 */
@Composable
internal fun PoolEntryCard(
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
internal fun LibraryStatsRow(
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
internal fun SelectionBar(
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
internal fun RenameLinkDialog(
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
internal fun CategoryPickerDialog(
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
