package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.Role
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.api.CloudUrlParser
import com.example.data.model.CloudItem
import com.example.ui.viewmodel.CloudLinkViewModel
import com.example.ui.viewmodel.DownloadViewModel

@Composable
fun MediaViewerScreen(
    item: CloudItem,
    viewModel: CloudLinkViewModel,
    downloadViewModel: DownloadViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    val mediaItems = currentFolder?.items?.filter { it.isImage || it.isVideo }.orEmpty()
    val currentMediaIndex = mediaItems.indexOfFirst { it.weblink == item.weblink }
    val hasPreviousMedia = currentMediaIndex > 0
    val hasNextMedia = currentMediaIndex >= 0 && currentMediaIndex < mediaItems.lastIndex
    var showOverlay by remember(item.weblink) { mutableStateOf(true) }

    DisposableEffect(activity, view) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        val wereSystemBarsVisible = window?.decorView?.rootWindowInsets?.let {
            WindowInsetsCompat.toWindowInsetsCompat(it).isVisible(WindowInsetsCompat.Type.systemBars())
        } ?: true

        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            if (wereSystemBarsVisible) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        onClose()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("media_viewer_screen")
    ) {
        val showFilmstrip = maxWidth >= 720.dp
        Row(modifier = Modifier.fillMaxSize()) rowScope@ {
            Box(
                modifier = if (showFilmstrip) Modifier.weight(1f).fillMaxHeight() else Modifier.fillMaxSize()
            ) {
                if (item.isVideo) {
                    VideoPlayerContent(
                        item = item,
                        viewModel = viewModel,
                        onTap = { showOverlay = !showOverlay }
                    )
                } else {
                    ZoomableImageContent(
                        item = item,
                        viewModel = viewModel,
                        onTap = { showOverlay = !showOverlay }
                    )
                }

                this@rowScope.AnimatedVisibility(
                    visible = showOverlay,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            color = Color(0xB3000000),
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onClose,
                                    modifier = Modifier.testTag("media_viewer_close")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.formattedSize,
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val fullUrl = CloudUrlParser.toFullPublicUrl(item.weblink)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, fullUrl)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Поделиться файлом"))
                                    }
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Поделиться", tint = Color.White)
                                }

                                IconButton(
                                    onClick = { downloadViewModel.downloadItem(item) },
                                    modifier = Modifier.testTag("media_viewer_download")
                                ) {
                                    Icon(
                                        Icons.Default.Download,
                                        contentDescription = "Скачать",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (hasPreviousMedia) {
                            IconButton(
                                onClick = { viewModel.previousMediaItem() },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 12.dp)
                                    .background(Color(0x80000000), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Предыдущий", tint = Color.White)
                            }
                        }

                        if (hasNextMedia) {
                            IconButton(
                                onClick = { viewModel.nextMediaItem() },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp)
                                    .background(Color(0x80000000), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Следующий", tint = Color.White)
                            }
                        }
                    }
                }
            }

            if (showFilmstrip) {
                MediaFilmstripPanel(
                    items = mediaItems,
                    currentItem = item,
                    folderTitle = currentFolder?.name ?: "Папка",
                    viewModel = viewModel,
                    modifier = Modifier.width(300.dp).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun MediaFilmstripPanel(
    items: List<CloudItem>,
    currentItem: CloudItem,
    folderTitle: String,
    viewModel: CloudLinkViewModel,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Text(
                    text = folderTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Фото и видео · ${items.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.weblink }) { mediaItem ->
                    val isCurrent = mediaItem.weblink == currentItem.weblink
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent
                            )
                            .selectable(
                                selected = isCurrent,
                                role = Role.Button,
                                onClick = { viewModel.openMediaViewer(mediaItem) }
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = viewModel.getThumbnailUrl(mediaItem.weblink, 128),
                            contentDescription = "Предпросмотр: ${mediaItem.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mediaItem.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (mediaItem.isVideo) "Видео · ${mediaItem.formattedSize}"
                                else "Фото · ${mediaItem.formattedSize}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImageContent(
    item: CloudItem,
    viewModel: CloudLinkViewModel,
    onTap: () -> Unit
) {
    var scale by remember(item.weblink) { mutableFloatStateOf(1f) }
    var offsetX by remember(item.weblink) { mutableFloatStateOf(0f) }
    var offsetY by remember(item.weblink) { mutableFloatStateOf(0f) }
    var isLoadingImage by remember(item.weblink) { mutableStateOf(true) }
    var imageError by remember(item.weblink) { mutableStateOf(false) }
    var retryAttempt by remember(item.weblink) { mutableIntStateOf(0) }

    // Use high-resolution 1024 thumb or direct download link for max fidelity
    val imageUrl = viewModel.getThumbnailUrl(item.weblink, 1024)

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
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        if (imageError) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    .padding(bottom = 32.dp)
                    .clickable {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FitScreen,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
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

@Composable
private fun VideoPlayerContent(
    item: CloudItem,
    viewModel: CloudLinkViewModel,
    onTap: () -> Unit
) {
    val streamingUrl = viewModel.getStreamingUrl(item.weblink)
    var isBuffering by remember(item.weblink) { mutableStateOf(true) }
    var videoError by remember(item.weblink) { mutableStateOf(false) }
    var retryAttempt by remember(item.weblink) { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
        contentAlignment = Alignment.Center
    ) {
        if (!videoError) {
            key(item.weblink, retryAttempt) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            var triedDirectFallback = false
                            val mediaController = MediaController(ctx)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)
                            setOnPreparedListener { mp ->
                                isBuffering = false
                                mp.isLooping = true
                                start()
                            }
                            setOnErrorListener { _, _, _ ->
                                if (!triedDirectFallback) {
                                    triedDirectFallback = true
                                    isBuffering = true
                                    val directUrl = viewModel.getDownloadUrl(item.weblink)
                                    setVideoURI(Uri.parse(directUrl))
                                    start()
                                } else {
                                    isBuffering = false
                                    videoError = true
                                }
                                true
                            }
                            setVideoURI(Uri.parse(streamingUrl))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (isBuffering && !videoError) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        if (videoError) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Не удалось воспроизвести видео",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Button(
                    onClick = {
                        videoError = false
                        isBuffering = true
                        retryAttempt++
                    }
                ) {
                    Text("Повторить")
                }
            }
        }
    }
}
