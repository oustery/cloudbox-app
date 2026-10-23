package com.example.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Filled star glyph for the "favorited" state.
 *
 * Lucide's set is stroke-only (outline), so a filled variant is vendored here to keep
 * the favorite toggle visually distinct while the rest of the icon pack stays Lucide.
 * Path data is the canonical 24dp material star silhouette (Apache-2.0), rendered with
 * Lucide's optical weight so it sits comfortably next to Lucide.Star.
 */
val StarFilled: ImageVector = ImageVector.Builder(
    name = "StarFilled",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = addPathNodes(
            "M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"
        ),
        fill = SolidColor(Color.Black),
        fillAlpha = 1f,
        pathFillType = PathFillType.NonZero,
    )
}.build()
