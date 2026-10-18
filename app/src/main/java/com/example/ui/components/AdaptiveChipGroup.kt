package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.theme.UiSpacing

/**
 * Lays out a compact set of chips across as many rows as needed, keeping every choice discoverable
 * at narrow widths and enlarged font scales without shrinking the label text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> AdaptiveChipGroup(
    items: List<T>,
    modifier: Modifier = Modifier,
    chipContent: @Composable (T) -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)
    ) {
        for (item in items) {
            chipContent(item)
        }
    }
}
