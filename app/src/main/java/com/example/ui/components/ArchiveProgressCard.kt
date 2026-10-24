package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.FolderDown
import com.composables.icons.lucide.X
import com.example.data.repository.ArchivePhase
import com.example.data.repository.ArchiveProgress
import com.example.ui.theme.UiRadius
import com.example.ui.theme.UiSpacing

/**
 * Floating card showing the ZIP-archive job state: collection/download
 * progress, the link currently being processed and a cancel button.
 * Rendered above the home FAB stack while a job is active.
 */
@Composable
fun ArchiveProgressCard(
    progress: ArchiveProgress?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = progress?.isActive == true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val state = progress ?: return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(UiRadius.large),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(UiRadius.large))
                .testTag("archive_progress_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = UiSpacing.large, vertical = UiSpacing.medium)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Lucide.FolderDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(UiSpacing.small))
                    Text(
                        text = "Создаём ZIP-архив",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(32.dp).testTag("archive_cancel_button")
                    ) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = "Отменить архивацию",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(UiSpacing.extraSmall))
                Text(
                    text = buildString {
                        state.currentLinkTitle?.let { append(it) }
                        if (state.filesTotal > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("Файл ${state.filesDone} из ${state.filesTotal}")
                        } else {
                            if (isNotEmpty()) append(" · ")
                            append("Ссылка ${state.linksDone + 1} из ${state.linksTotal}")
                        }
                        append(" · ")
                        append(formatArchiveBytes(state.bytesWritten))
                        if (state.bytesTotal > 0) append(" из ${formatArchiveBytes(state.bytesTotal)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(UiSpacing.small))
                val fraction = state.downloadFraction
                if (state.phase == ArchivePhase.DOWNLOADING && fraction != null) {
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun formatArchiveBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes / (1024f * 1024f * 1024f))
    bytes >= 1L shl 20 -> "%.1f МБ".format(bytes / (1024f * 1024f))
    bytes > 0 -> "%d КБ".format(bytes / 1024)
    else -> "0 Б"
}
