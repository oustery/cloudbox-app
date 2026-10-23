package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.ui.components.expressive.WavyCircularProgress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.CloudItem
import com.example.data.repository.ThumbnailSize
import com.example.ui.theme.UiSpacing

@Composable
internal fun ZoomableImageContent(
    item: CloudItem,
    viewModel: com.example.ui.viewmodel.CloudLinkViewModel,
    onTap: () -> Unit
) {
    var scale by remember(item.weblink) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.weblink) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.weblink) { mutableFloatStateOf(0f) }
    var isLoadingImage by remember(item.weblink) { mutableStateOf(true) }
    var imageError by remember(item.weblink) { mutableStateOf(false) }
    var retryAttempt by remember(item.weblink) { mutableIntStateOf(0) }

    // High-resolution preview token; the source file itself is rarely needed.
    val imageUrl = viewModel.getThumbnailUrl(item.weblink, ThumbnailSize.HIGH_RES)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = nextScale
                    if (nextScale > 1f) {
                        val maxOffsetX = size.width * (nextScale - 1f) / 2f
                        val maxOffsetY = size.height * (nextScale - 1f) / 2f
                        offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!imageError) {
            key(retryAttempt) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    onLoading = { isLoadingImage = true },
                    onSuccess = {
                        isLoadingImage = false
                        imageError = false
                    },
                    onError = {
                        isLoadingImage = false
                        imageError = true
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }
        }

        if (isLoadingImage && !imageError) {
            WavyCircularProgress(color = PlayerAccent)
        }

        if (imageError) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(UiSpacing.doubleExtraLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
            ) {
                Text(
                    text = "Не удалось загрузить изображение",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Button(
                    onClick = {
                        imageError = false
                        isLoadingImage = true
                        retryAttempt++
                    }
                ) {
                    Text("Повторить")
                }
            }
        }

        // Quick zoom reset button when zoomed in
        if (scale > 1.05f && !imageError) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xCC000000),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = UiSpacing.doubleExtraLarge)
                    .clickable {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = UiSpacing.medium, vertical = UiSpacing.iconGap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FitScreen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(UiSpacing.iconGap))
                    Text(
                        text = "${(scale * 100).toInt()}% • Сбросить зум",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

