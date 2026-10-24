package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import com.example.BuildConfig
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AppFont
import com.example.data.preferences.AppIconVariant
import com.example.data.preferences.AppThemeMode
import com.example.data.preferences.GridDensity
import com.example.data.preferences.StartScreen
import com.example.data.preferences.AppSettingsViewModel
import com.example.data.update.AppUpdateManager
import com.example.ui.components.AdaptiveChipGroup
import com.example.ui.core.components.ConfirmDialog
import com.example.ui.core.feedback.UiMessenger
import com.example.ui.theme.UiSpacing
import com.example.ui.theme.toFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class CacheStats(
    val memoryBytes: Long = 0L,
    val diskBytes: Long = 0L,
    val mediaBytes: Long = 0L
) {
    val totalBytes: Long get() = memoryBytes + diskBytes + mediaBytes
}

@Composable
fun SettingsScreen(
    settingsViewModel: AppSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val explorerGridView by settingsViewModel.explorerGridView.collectAsStateWithLifecycle()
    val explorerGridDensity by settingsViewModel.explorerGridDensity.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    // Cache statistics are re-read every time [cacheRevision] increments, i.e.
    // right after the user wipes the cache (revision 0 = initial read only).
    var cacheRevision by remember { mutableIntStateOf(0) }
    var cacheStats by remember { mutableStateOf<CacheStats?>(null) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    LaunchedEffect(cacheRevision) {
        if (cacheRevision > 0) {
            val ok = withContext(Dispatchers.IO) { clearAllCaches(context) }
            UiMessenger.show(if (ok) "Кэш очищен" else "Не удалось очистить кэш полностью")
        }
        cacheStats = withContext(Dispatchers.IO) { readCacheStats(context) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = UiSpacing.extraLarge, vertical = UiSpacing.large),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.large)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)) {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Поведение, внешний вид и хранилище CloudBox.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsSection(title = "Основные") {
            SectionLabel("Стартовый экран", "Какой раздел открывать при запуске приложения.")
            AdaptiveChipGroup(
                items = StartScreen.entries.toList(),
                modifier = Modifier.fillMaxWidth()
            ) { screen ->
                FilterChip(
                    selected = settings.startScreen == screen,
                    onClick = { settingsViewModel.setStartScreen(screen) },
                    label = { Text(screen.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("start_screen_${screen.key}")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceSwitchRow(
                title = "Виброотклик",
                description = "Тактильное подтверждение действий: избранное, вкладки, загрузки.",
                checked = settings.hapticsEnabled,
                enabled = true,
                onCheckedChange = settingsViewModel::setHapticsEnabled
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceSwitchRow(
                title = "Проверять обновления автоматически",
                description = "Приложение само заглянет в релизы GitHub при запуске.",
                checked = settings.autoUpdateCheck,
                enabled = true,
                onCheckedChange = settingsViewModel::setAutoUpdateCheck
            )
        }

        SettingsSection(title = "Внешний вид") {
            SectionLabel("Тема приложения", "Системная тема автоматически следует настройке устройства.")
            AdaptiveChipGroup(
                items = AppThemeMode.entries.toList(),
                modifier = Modifier.fillMaxWidth()
            ) { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { settingsViewModel.setThemeMode(mode) },
                    label = { Text(mode.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceSwitchRow(
                title = "Динамические цвета Material You",
                description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    "Использовать палитру, созданную из обоев устройства."
                } else {
                    "Доступно на Android 12 и новее."
                },
                checked = settings.dynamicColors,
                enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                onCheckedChange = settingsViewModel::setDynamicColors
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel("Шрифт интерфейса", "Отрисовывается мгновенно, применяется ко всем экранам.")
            AdaptiveChipGroup(
                items = AppFont.entries.toList(),
                modifier = Modifier.fillMaxWidth()
            ) { font ->
                FilterChip(
                    selected = settings.font == font,
                    onClick = { settingsViewModel.setFont(font) },
                    label = {
                        Text(
                            font.title,
                            fontFamily = font.toFontFamily(),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("font_${font.key}")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel("Иконка приложения", "Лаунчер обновит значок через несколько секунд после выбора.")
            AdaptiveChipGroup(
                items = AppIconVariant.entries.toList(),
                modifier = Modifier.fillMaxWidth()
            ) { variant ->
                FilterChip(
                    selected = settings.iconVariant == variant,
                    onClick = { settingsViewModel.setIconVariant(variant) },
                    label = { Text(variant.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("icon_variant_${variant.key}")
                )
            }
        }

        SettingsSection(title = "Проводник") {
            SectionLabel("Вид файлов", "Запоминается между сессиями — проводник откроется так же, как вы его оставили.")
            AdaptiveChipGroup(
                items = listOf(true, false),
                modifier = Modifier.fillMaxWidth()
            ) { isGrid ->
                FilterChip(
                    selected = explorerGridView == isGrid,
                    onClick = { settingsViewModel.setExplorerGridView(isGrid) },
                    label = { Text(if (isGrid) "Сетка" else "Список") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel("Плотность сетки", "Сколько плиток файлов помещается в ряд в режиме сетки.")
            AdaptiveChipGroup(
                items = GridDensity.entries.toList(),
                modifier = Modifier.fillMaxWidth()
            ) { density ->
                FilterChip(
                    selected = explorerGridDensity == density,
                    onClick = { settingsViewModel.setExplorerGridDensity(density) },
                    label = { Text(density.title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier.testTag("grid_density_${density.name}")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PreferenceSwitchRow(
                title = "Экономия трафика",
                description = "Не загружать превью в проводнике — быстрее листать большие папки на мобильном интернете.",
                checked = settings.dataSaver,
                enabled = true,
                onCheckedChange = settingsViewModel::setDataSaver
            )
        }

        SettingsSection(title = "Хранилище") {
            val stats = cacheStats
            if (stats != null) {
                CacheStatRow("Превью в памяти", formatBytes(stats.memoryBytes))
                CacheStatRow("Превью на диске", formatBytes(stats.diskBytes))
                CacheStatRow("Кэш видео", formatBytes(stats.mediaBytes))
                Spacer(modifier = Modifier.height(UiSpacing.extraSmall))
                CacheStatRow(
                    "Всего",
                    formatBytes(stats.totalBytes),
                    valueColor = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    "Считаем занятое место…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(UiSpacing.extraSmall))
            OutlinedButton(
                onClick = { showClearCacheDialog = true },
                enabled = (cacheStats?.totalBytes ?: 0L) > 0L,
                modifier = Modifier.testTag("clear_cache_button")
            ) {
                Text("Очистить кэш")
            }
            Text(
                "Кэш ускоряет повторный просмотр: превью и уже скачанные фрагменты видео не грузятся заново. Очистка не удаляет сохранённые загрузки.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsSection(title = "О приложении") {
            AboutRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            AboutRow("Сборка", BuildConfig.GIT_SHA)
            AboutRow("Собрано", formatBuildTime(BuildConfig.BUILD_TIME))
            Spacer(modifier = Modifier.height(UiSpacing.extraSmall))
            OutlinedButton(
                onClick = { AppUpdateManager.openReleasePage(context) },
                modifier = Modifier.testTag("open_releases_page")
            ) {
                Text("Страница релизов и обновлений")
            }
        }

        Text(
            "Настройки сохраняются на этом устройстве.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showClearCacheDialog) {
        ConfirmDialog(
            title = "Очистить кэш?",
            text = "Превью и кэш видео будут удалены. Скачанные файлы останутся на месте.",
            confirmLabel = "Очистить",
            isDanger = true,
            onConfirm = {
                showClearCacheDialog = false
                cacheRevision++ // triggers wipe + stats recomputation
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
        ) {
            Column(
                modifier = Modifier.padding(UiSpacing.large),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CacheStatRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}

// Reused across recompositions; UI calls are main-thread only.
private val buildTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

private fun formatBuildTime(epochMillis: Long): String = try {
    buildTimeFormat.format(Date(epochMillis))
} catch (e: Exception) {
    ""
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 20 -> "%.1f МБ".format(bytes / 1048576f)
    bytes >= 1024L -> "%d КБ".format(bytes / 1024L)
    bytes > 0L -> "$bytes Б"
    else -> "0 Б"
}

@AndroidXOptIn(androidx.media3.common.util.UnstableApi::class)
private fun readCacheStats(context: android.content.Context): CacheStats = runCatching {
    val loader = context.applicationContext.imageLoader
    CacheStats(
        memoryBytes = loader.memoryCache?.size?.toLong() ?: 0L,
        diskBytes = loader.diskCache?.size?.toLong() ?: 0L,
        mediaBytes = PlayerMediaCache.sizeBytes()
    )
}.getOrDefault(CacheStats())

@AndroidXOptIn(androidx.media3.common.util.UnstableApi::class)
private fun clearAllCaches(context: android.content.Context): Boolean {
    var ok = true
    runCatching {
        val loader = context.applicationContext.imageLoader
        loader.memoryCache?.clear()
        loader.diskCache?.clear()
    }.onFailure { ok = false }
    if (!PlayerMediaCache.clear()) ok = false
    return ok
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = if (checked) "Включено" else "Выключено"
            }
        )
    }
}
