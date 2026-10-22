package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.theme.UiSpacing

/**
 * Skeleton placeholders shown while the folder content is being fetched for the
 * first time. Mirrors the real grid/list card geometry so the layout does not
 * jump once actual items arrive.
 */
@Composable
fun FolderSkeletonPlaceholder(isGrid: Boolean, modifier: Modifier = Modifier) {
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(UiSpacing.large),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.medium),
            modifier = modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            items(count = SKELETON_ITEM_COUNT, key = { it }) { index ->
                SkeletonGridCard()
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(UiSpacing.large),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.small),
            modifier = modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            items(count = SKELETON_ITEM_COUNT, key = { it }) { index ->
                SkeletonListRow()
            }
        }
    }
}

private const val SKELETON_ITEM_COUNT = 12

@Composable
private fun SkeletonGridCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(14.dp))
            .shimmerPlaceholder()
    )
}

@Composable
private fun SkeletonListRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .shimmerPlaceholder()
            .padding(UiSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .backgroundPlaceholder()
        )
        Spacer(modifier = Modifier.width(UiSpacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .backgroundPlaceholder()
            )
            Spacer(modifier = Modifier.height(UiSpacing.small))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(11.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .backgroundPlaceholder()
            )
        }
    }
}

@Composable
private fun Modifier.backgroundPlaceholder(): Modifier =
    background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), CircleShape)
