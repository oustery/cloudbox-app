package com.example.ui.components

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.theme.UiSpacing

/**
 * Lays out a compact set of chips across as many rows as needed, keeping every choice discoverable
 * at narrow widths and enlarged font scales without shrinking the label text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveChipGroup(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.small),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall),
        content = content
    )
}
