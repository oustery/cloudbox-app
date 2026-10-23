package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * Custom transport seek bar with a buffered-range indicator.
 *
 * Implemented with Canvas + raw gesture detectors because [androidx.compose.material3.Slider]
 * cannot display secondary (buffered) progress.
 */
@Composable
internal fun VideoSeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeekStarted: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragFraction by remember { mutableFloatStateOf(Float.NaN) }
    val playedFraction = if (!dragFraction.isNaN()) {
        dragFraction
    } else if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f
    val bufferedFraction = if (durationMs > 0) {
        (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f

    fun fractionToMs(fraction: Float): Long =
        (fraction.coerceIn(0f, 1f) * durationMs).toLong()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(durationMs, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeekStarted()
                        val target = fractionToMs(fraction)
                        onSeek(target)
                        onSeekFinished(target)
                        dragFraction = Float.NaN
                    }
                )
            }
            .pointerInput(durationMs, enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onSeekStarted()
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(fractionToMs(dragFraction))
                    },
                    onDragEnd = {
                        if (!dragFraction.isNaN()) {
                            val target = fractionToMs(dragFraction)
                            onSeekFinished(target)
                        }
                        dragFraction = Float.NaN
                    },
                    onDragCancel = {
                        if (!dragFraction.isNaN()) {
                            onSeekFinished(fractionToMs(dragFraction))
                        }
                        dragFraction = Float.NaN
                    }
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(fractionToMs(dragFraction))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeight = 4.dp.toPx()
            val centerY = size.height / 2f
            val cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.24f),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = cornerRadius
            )
            val bufferedWidth = size.width * bufferedFraction
            if (bufferedWidth > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(bufferedWidth, trackHeight),
                    cornerRadius = cornerRadius
                )
            }
            val playedWidth = size.width * playedFraction
            drawRoundRect(
                color = PlayerAccent,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(playedWidth, trackHeight),
                cornerRadius = cornerRadius
            )
            drawCircle(
                color = PlayerAccent,
                radius = 7.dp.toPx(),
                center = Offset(playedWidth, centerY)
            )
        }
    }
}

