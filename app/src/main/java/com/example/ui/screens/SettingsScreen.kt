package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.data.preferences.AppSettings
import com.example.data.preferences.AppThemeMode
import com.example.ui.components.AdaptiveChipGroup
import com.example.ui.theme.UiSpacing

@Composable
fun SettingsScreen(
    settings: AppSettings,
    explorerGridView: Boolean,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDynamicColorsChanged: (Boolean) -> Unit,
    onExplorerGridViewChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(UiSpacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.large)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)) {
            Text("Настройки", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Настройте внешний вид CloudBox под себя.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text("Внешний вид", style = MaterialTheme.typography.titleMedium)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
        ) {
            Column(
                modifier = Modifier.padding(UiSpacing.large),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)) {
                    Text("Тема приложения", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Системная тема автоматически следует настройке устройства.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AdaptiveChipGroup(
                    items = AppThemeMode.entries.toList(),
                    modifier = Modifier.fillMaxWidth()
                ) { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeModeChanged(mode) },
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
                    onCheckedChange = onDynamicColorsChanged
                )
            }
        }

        Text("Проводник", style = MaterialTheme.typography.titleMedium)
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
                Text("Вид файлов", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Запоминается между сессиями — проводник откроется так же, как вы его оставили.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AdaptiveChipGroup(
                    items = listOf(true, false),
                    modifier = Modifier.fillMaxWidth()
                ) { isGrid ->
                    FilterChip(
                        selected = explorerGridView == isGrid,
                        onClick = { onExplorerGridViewChanged(isGrid) },
                        label = { Text(if (isGrid) "Сетка" else "Список") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(UiSpacing.extraSmall))
        Text(
            "Настройки сохраняются на этом устройстве.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
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
