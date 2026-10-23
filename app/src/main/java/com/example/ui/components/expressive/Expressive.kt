package com.example.ui.components.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * A [Shape] that renders an intermediate polygon between two Material shapes
 * at the given morph [progress] (0f = start shape, 1f = end shape).
 *
 * The path produced by [Morph.toPath] lives in the normalized 0..1 space, so it
 * is scaled to the incoming [Size] and re-centered exactly like Material's own
 * [RoundedPolygon.toShape] implementation.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path: Path = morph.toPath(progress)
        val scaleMatrix = Matrix().apply { scale(x = size.width, y = size.height) }
        path.transform(scaleMatrix)
        val bounds = path.getBounds()
        path.translate(
            Offset(
                x = size.width / 2f - bounds.center.x,
                y = size.height / 2f - bounds.center.y,
            )
        )
        return Outline.Generic(path)
    }
}

/**
 * Canonical Material 3 Expressive icon button: the container morphs between
 * [idleShape] and [pressedShape] with the theme's expressive spatial spring
 * while pressed. The morph itself replaces the ripple as the press indication.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MorphingIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    idleShape: RoundedPolygon = MaterialShapes.Circle,
    pressedShape: RoundedPolygon = MaterialShapes.Cookie12Sided,
    containerColor: Color = Color.Transparent,
    contentColor: Color = LocalContentColor.current,
    enabled: Boolean = true,
    iconSize: Dp = 24.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val morph = remember(idleShape, pressedShape) { Morph(idleShape, pressedShape) }
    val morphProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "morphingIconButtonProgress",
    )
    val shape = MorphPolygonShape(morph, morphProgress)

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .clip(shape)
            .background(containerColor, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Canonical M3 Expressive shape-shifting loading indicator (morphs between
 * Material shapes while visible), tinted with the brand color by default.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    polygons: List<RoundedPolygon> = listOf(
        MaterialShapes.Cookie12Sided,
        MaterialShapes.Square,
        MaterialShapes.Sunny,
        MaterialShapes.Circle,
        MaterialShapes.Pill,
        MaterialShapes.SoftBurst,
    ),
) {
    LoadingIndicator(
        modifier = modifier,
        color = color,
        polygons = polygons,
    )
}

/** Determinate wavy linear progress — the canonical download/transfer indicator. */
@Composable
fun WavyLinearProgress(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LinearWavyProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
    )
}

/** Indeterminate wavy linear progress. */
@Composable
fun WavyLinearProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LinearWavyProgressIndicator(
        modifier = modifier,
        color = color,
    )
}

/** Compact circular wavy progress for inline/busy spots. */
@Composable
fun WavyCircularProgress(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularWavyProgressIndicator(
        modifier = modifier,
        color = color,
    )
}
