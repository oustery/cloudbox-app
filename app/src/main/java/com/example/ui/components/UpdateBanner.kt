package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.update.AppUpdateInfo
import com.example.data.update.UpdateUiState
import com.example.ui.components.expressive.WavyCircularProgress
import java.util.Locale
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CloudDownload
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.X

/**
 * Compact banner shown on the home screen when a newer CloudBox build is available on
 * GitHub. Renders nothing for [UpdateUiState.Idle] / [UpdateUiState.UpToDate].
 */
@Composable
fun UpdateBanner(
    state: UpdateUiState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateIsVisible = state is UpdateUiState.Available ||
        state is UpdateUiState.Downloading ||
        state is UpdateUiState.ReadyToInstall
    if (!stateIsVisible) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        when (val current = state) {
            is UpdateUiState.Available -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Lucide.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Доступна новая сборка",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = describeBuild(current.info),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onOpenRelease) {
                        Icon(
                            imageVector = Lucide.ExternalLink,
                            contentDescription = "Что нового",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    FilledTonalButton(
                        onClick = onDownload,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 14.dp, vertical = 4.dp
                        )
                    ) {
                        Text("Скачать")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = "Скрыть",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            is UpdateUiState.Downloading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Material 3 Expressive circular wavy indicator.
                    WavyCircularProgress(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Загружаем обновление…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            is UpdateUiState.ReadyToInstall -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Lucide.CircleCheck,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Обновление загружено",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Установка займёт несколько секунд",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(onClick = onInstall) {
                        Text("Установить")
                    }
                }
            }

            else -> Unit
        }
    }
}

private fun describeBuild(info: AppUpdateInfo): String {
    val parts = mutableListOf<String>()
    if (info.buildNumber > 0L) {
        parts.add("сборка #%d".format(Locale.getDefault(), info.buildNumber))
    } else if (info.commitSha.isNotBlank()) {
        parts.add(info.commitSha.take(7))
    }
    if (info.apkSizeBytes > 0L) {
        parts.add(
            String.format(Locale.getDefault(), "%.1f МБ", info.apkSizeBytes / 1_048_576.0)
        )
    }
    return if (parts.isEmpty()) info.title else parts.joinToString(" • ")
}
