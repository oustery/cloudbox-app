package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.api.CloudUrlParser
import com.example.data.model.CloudItem
import com.example.ui.theme.UiMotionTokens
import com.example.ui.theme.UiSpacing
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Info

@Composable
fun MediaViewerScreen(
    item: CloudItem,
    viewModel: com.example.ui.viewmodel.CloudLinkViewModel,
    downloadViewModel: com.example.ui.viewmodel.DownloadViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val currentFolder by viewModel.currentFolder.collectAsStateWithLifecycle()
    // Playlist-aware: when the viewer was opened from the unified "Все файлы"
    // pool, this returns the merged pool playlist instead of one folder's media.
    val mediaItems = viewModel.getActiveMediaItems()
    val currentMediaIndex = mediaItems.indexOfFirst { it.weblink == item.weblink }
    val hasPreviousMedia = currentMediaIndex > 0
    val hasNextMedia = currentMediaIndex >= 0 && currentMediaIndex < mediaItems.lastIndex
    var showOverlay by remember(item.weblink) { mutableStateOf(true) }
    var showMetadata by remember(item.weblink) { mutableStateOf(false) }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, window.decorView) }
        val wereSystemBarsVisible = window?.decorView?.rootWindowInsets?.let {
            WindowInsetsCompat.toWindowInsetsCompat(it, window.decorView).isVisible(WindowInsetsCompat.Type.systemBars())
        } ?: true
        val previousSystemBarsBehavior = controller?.systemBarsBehavior

        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onDispose {
            previousSystemBarsBehavior?.let { controller?.systemBarsBehavior = it }
            if (wereSystemBarsVisible) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(activity, showOverlay) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (showOverlay) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.hide(WindowInsetsCompat.Type.systemBars())
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
                if (item.isVideo && viewModel.isMegaItem(item.weblink)) {
                    // MEGA video cannot be streamed client-side (encrypted
                    // content, no local proxy): show a graceful fallback.
                    MegaVideoFallback(
                        item = item,
                        modifier = Modifier.fillMaxSize(),
                        onDownload = { downloadViewModel.downloadItem(item) }
                    )
                } else if (item.isVideo) {
                    VideoPlayerContent(
                        item = item,
                        viewModel = viewModel,
                        downloadViewModel = downloadViewModel,
                        showOverlay = showOverlay,
                        onToggleOverlay = { showOverlay = !showOverlay }
                    )

                    this@rowScope.AnimatedVisibility(
                        visible = showOverlay,
                        enter = fadeIn(animationSpec = tween(UiMotionTokens.shortDurationMillis, easing = UiMotionTokens.standardEasing)),
                        exit = fadeOut(animationSpec = tween(UiMotionTokens.shortDurationMillis, easing = UiMotionTokens.standardEasing)),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        MediaViewerTopBar(
                            item = item,
                            onClose = onClose,
                            onShare = {
                                val fullUrl = CloudUrlParser.toFullPublicUrl(item.weblink)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, fullUrl)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Поделиться файлом"))
                            },
                            onDownload = { downloadViewModel.downloadItem(item) }
                        )
                    }

                    this@rowScope.AnimatedVisibility(
                        visible = showOverlay,
                        enter = fadeIn(animationSpec = tween(UiMotionTokens.shortDurationMillis)),
                        exit = fadeOut(animationSpec = tween(UiMotionTokens.shortDurationMillis)),
                        modifier = Modifier.align(Alignment.CenterStart)
                    ) {
                        if (hasPreviousMedia) {
                            IconButton(
                                onClick = { viewModel.previousMediaItem() },
                                modifier = Modifier
                                    .padding(start = UiSpacing.medium)
                                    .background(Color(0x80000000), CircleShape)
                                    .testTag("media_prev")
                            ) {
                                Icon(Lucide.ArrowLeft, contentDescription = "Предыдущий", tint = Color.White)
                            }
                        }
                    }

                    this@rowScope.AnimatedVisibility(
                        visible = showOverlay,
                        enter = fadeIn(animationSpec = tween(UiMotionTokens.shortDurationMillis)),
                        exit = fadeOut(animationSpec = tween(UiMotionTokens.shortDurationMillis)),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        if (hasNextMedia) {
                            IconButton(
                                onClick = { viewModel.nextMediaItem() },
                                modifier = Modifier
                                    .padding(end = UiSpacing.medium)
                                    .background(Color(0x80000000), CircleShape)
                                    .testTag("media_next")
                            ) {
                                Icon(Lucide.ArrowRight, contentDescription = "Следующий", tint = Color.White)
                            }
                        }
                    }

                    // Video metadata lives inside the player control deck
                    // (handled by VideoPlayerContent) to avoid overlapping UI.
                } else {
                    ZoomableImageContent(
                        item = item,
                        viewModel = viewModel,
                        onTap = { showOverlay = !showOverlay }
                    )

                    this@rowScope.AnimatedVisibility(
                        visible = showOverlay,
                        enter = fadeIn(animationSpec = tween(UiMotionTokens.shortDurationMillis, easing = UiMotionTokens.standardEasing)),
                        exit = fadeOut(animationSpec = tween(UiMotionTokens.shortDurationMillis, easing = UiMotionTokens.standardEasing)),
                        modifier = Modifier.align(Alignment.TopCenter)
                    ) {
                        MediaViewerTopBar(
                            item = item,
                            onClose = onClose,
                            onShare = {
                                val fullUrl = CloudUrlParser.toFullPublicUrl(item.weblink)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, fullUrl)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Поделиться файлом"))
                            },
                            onDownload = { downloadViewModel.downloadItem(item) }
                        )
                    }

                    if (showOverlay && hasPreviousMedia) {
                        IconButton(
                            onClick = { viewModel.previousMediaItem() },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = UiSpacing.medium)
                                .background(Color(0x80000000), CircleShape)
                        ) {
                            Icon(Lucide.ArrowLeft, contentDescription = "Предыдущий", tint = Color.White)
                        }
                    }

                    if (showOverlay && hasNextMedia) {
                        IconButton(
                            onClick = { viewModel.nextMediaItem() },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = UiSpacing.medium)
                                .background(Color(0x80000000), CircleShape)
                        ) {
                            Icon(Lucide.ArrowRight, contentDescription = "Следующий", tint = Color.White)
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = UiSpacing.medium, vertical = UiSpacing.small),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(UiSpacing.small)
                    ) {
                        AnimatedVisibility(visible = showMetadata && showOverlay) {
                            MediaMetadataPanel(item = item, modifier = Modifier.fillMaxWidth())
                        }
                        if (showOverlay) {
                            Surface(color = Color(0xCC000000), shape = MaterialTheme.shapes.extraLarge) {
                                IconButton(
                                    onClick = { showMetadata = !showMetadata },
                                    modifier = Modifier.testTag("media_viewer_metadata_toggle")
                                ) {
                                    Icon(
                                        imageVector = Lucide.Info,
                                        contentDescription = if (showMetadata) "Скрыть сведения" else "Показать сведения",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showFilmstrip) {
                MediaFilmstripPanel(
                    items = mediaItems,
                    currentItem = item,
                    folderTitle = currentFolder?.name ?: "Все файлы",
                    viewModel = viewModel,
                    modifier = Modifier.width(300.dp).fillMaxHeight()
                )
            }
        }
    }
}

