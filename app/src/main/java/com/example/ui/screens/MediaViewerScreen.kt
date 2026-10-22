package com.example.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.StandaloneDatabaseProvider
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.CloudUrlParser
import com.example.data.model.CloudItem
import com.example.data.repository.ThumbnailSize
import com.example.ui.theme.UiMotionTokens
import com.example.ui.theme.UiSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Fixed accent tuned for legibility on top of video frames in both themes. */
private val PlayerAccent = Color(0xFFFF8FA8)
private val PlayerOnSurface = Color.White

private const val PLAYER_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Process-wide LRU media cache. Streams downloaded from the Mail.ru shards are
 * stored here so that seek-backs, replays and re-opened videos start instantly
 * instead of re-fetching megabytes over the high-latency public endpoints.
 */
object PlayerMediaCache {
    private const val MAX_CACHE_BYTES = 256L * 1024 * 1024

    @Volatile
    private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache = instance ?: synchronized(this) {
        instance ?: SimpleCache(
            java.io.File(context.cacheDir, "cloudbox_media_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context)
        ).also { instance = it }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

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
    val mediaItems = currentFolder?.items?.filter { it.isImage || it.isVideo }.orEmpty()
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
                if (item.isVideo) {
                    VideoPlayerContent(
                        item = item,
                        viewModel = viewModel,
                        downloadViewModel = downloadViewModel,
                        showOverlay = showOverlay,
                        onToggleOverlay = { showOverlay = !showOverlay }
                    )

                    AnimatedVisibility(
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

                    AnimatedVisibility(
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
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Предыдущий", tint = Color.White)
                            }
                        }
                    }

                    AnimatedVisibility(
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
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Следующий", tint = Color.White)
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

                    AnimatedVisibility(
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Предыдущий", tint = Color.White)
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
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Следующий", tint = Color.White)
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
                                        imageVector = Icons.Default.Info,
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
                    folderTitle = currentFolder?.name ?: "Папка",
                    viewModel = viewModel,
                    modifier = Modifier.width(300.dp).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
private fun MediaViewerTopBar(
    item: CloudItem,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xB3000000),
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .testTag("media_viewer_top_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = UiSpacing.small, vertical = UiSpacing.iconGap),
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

            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Поделиться", tint = Color.White)
            }

            IconButton(
                onClick = onDownload,
                modifier = Modifier.testTag("media_viewer_download")
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Скачать",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Streams a cloud video through Media3/ExoPlayer instead of the legacy VideoView.
 *
 * Key playback decisions (all measured against the live Mail.ru public shards):
 *  - The public /weblink/view/ endpoint answers with a signed 301 redirect and
 *    ~0.8-1.1 s time-to-first-byte, so the load control pre-buffers a generous
 *    window (25 s before playback, up to 120 s ahead) to survive stalls.
 *  - A process-wide [SimpleCache] keeps the most recent 256 MB of streamed data,
 *    making seek-backs and replays effectively instant.
 *  - Media3 ships an AVI extractor, which restores playback for the numerous
 *    .avi files that the platform MediaPlayer cannot even demux.
 */
@Composable
private fun VideoPlayerContent(
    item: CloudItem,
    viewModel: com.example.ui.viewmodel.CloudLinkViewModel,
    downloadViewModel: com.example.ui.viewmodel.DownloadViewModel,
    showOverlay: Boolean,
    onToggleOverlay: () -> Unit
) {
    val context = LocalContext.current
    val streamingUrl = viewModel.getStreamingUrl(item.weblink)
    var isBuffering by remember(item.weblink) { mutableStateOf(true) }
    var videoError by remember(item.weblink) { mutableStateOf<String?>(null) }
    var retryAttempt by remember(item.weblink) { mutableIntStateOf(0) }
    var hasFirstFrame by remember(item.weblink) { mutableStateOf(false) }

    var isPlaying by remember(item.weblink) { mutableStateOf(false) }
    var isMuted by remember(item.weblink) { mutableStateOf(false) }
    var isLoopingOne by remember(item.weblink) { mutableStateOf(false) }
    var playbackSpeed by remember(item.weblink) { mutableFloatStateOf(1f) }
    var durationMs by remember(item.weblink) { mutableLongStateOf(0L) }
    var positionMs by remember(item.weblink) { mutableLongStateOf(0L) }
    var bufferedMs by remember(item.weblink) { mutableLongStateOf(0L) }
    var isSeeking by remember(item.weblink) { mutableStateOf(false) }
    var seekPositionMs by remember(item.weblink) { mutableLongStateOf(0L) }
    var speedMenuOpen by remember { mutableStateOf(false) }
    var showMetadata by remember(item.weblink) { mutableStateOf(false) }
    var seekFeedback by remember { mutableIntStateOf(0) } // -1 back, +1 forward, 0 none
    var isFullscreenRequested by remember { mutableStateOf(false) }

    val activity = context.findActivity()

    val resumePosition = remember(item.weblink) { viewModel.getVideoResumePosition(item.weblink) }

    val player = remember(item.weblink, retryAttempt) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        val upstream: DataSource.Factory = CacheDataSource.Factory()
            .setCache(PlayerMediaCache.get(context))
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(
                CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or
                    CacheDataSource.FLAG_BLOCK_ON_CACHE
            )

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 60_000,
                /* maxBufferMs = */ 120_000,
                /* bufferForPlaybackMs = */ 25_000,
                /* bufferForPlaybackAfterRebufferMs = */ 45_000
            )
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(upstream))
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setMediaItem(
                    MediaItem.Builder()
                        .setUri(streamingUrl)
                        .build(),
                    resumePosition.takeIf { it > 3_000L } ?: C.TIME_UNSET
                )
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    viewModel.clearVideoPosition(item.weblink)
                }
            }

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                videoError = error.errorCodeName
            }

            override fun onRenderedFirstFrame() {
                hasFirstFrame = true
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            if (player.currentPosition > 0 && !player.playbackState.let { it == Player.STATE_ENDED }) {
                viewModel.rememberVideoPosition(item.weblink, player.currentPosition)
            }
            player.release()
        }
    }

    // Poll transport state for the seek bar.
    LaunchedEffect(player) {
        while (isActive) {
            if (!isSeeking) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
                if (durationMs <= 0L && player.playbackState == Player.STATE_READY) {
                    durationMs = player.duration.coerceAtLeast(0L)
                }
            }
            delay(500)
        }
    }

    // Auto-hide the controls while playback is running.
    LaunchedEffect(showOverlay, isPlaying) {
        if (showOverlay && isPlaying) {
            delay(3500)
            onToggleOverlay()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(item.weblink) {
                detectTapGestures(
                    onTap = { onToggleOverlay() },
                    onDoubleTap = { offset ->
                        val backward = offset.x < size.width / 2f
                        val target = if (backward) {
                            (player.currentPosition - 10_000L).coerceAtLeast(0L)
                        } else {
                            (player.currentPosition + 10_000L).coerceAtMost(player.duration.coerceAtLeast(0L))
                        }
                        player.seekTo(target)
                        seekFeedback = if (backward) -1 else 1
                    }
                )
            }
    ) {
        // Video surface (always composed so the player keeps rendering).
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { playerView ->
                playerView.player = player
            },
            modifier = Modifier.fillMaxSize()
        )

        // Poster frame visible until the first real frame is rendered.
        if (!hasFirstFrame) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(viewModel.getThumbnailUrl(item.weblink, ThumbnailSize.VIDEO_POSTER))
                    .crossfade(200)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isBuffering && videoError == null) {
            CircularProgressIndicator(
                color = PlayerAccent,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Double-tap seek feedback.
        AnimatedVisibility(
            visible = seekFeedback != 0,
            enter = fadeIn(animationSpec = tween(UiMotionTokens.shortDurationMillis)),
            exit = fadeOut(animationSpec = tween(UiMotionTokens.shortDurationMillis)),
            modifier = Modifier.align(
                if (seekFeedback >= 0) Alignment.CenterEnd else Alignment.CenterStart
            )
        ) {
            Surface(
                color = Color(0x99000000),
                shape = CircleShape,
                modifier = Modifier.padding(UiSpacing.extraLarge)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = UiSpacing.large, vertical = UiSpacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (seekFeedback >= 0) Icons.Default.Forward10 else Icons.Default.Replay10,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(Modifier.width(UiSpacing.iconGap))
                    Text(
                        text = "10 сек",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        LaunchedEffect(seekFeedback) {
            if (seekFeedback != 0) {
                delay(600)
                seekFeedback = 0
            }
        }

        // Big play button when paused.
        if (!isPlaying && videoError == null && !isBuffering) {
            Surface(
                color = Color(0x99000000),
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(72.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { player.play() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Воспроизвести",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        if (videoError != null) {
            VideoErrorPanel(
                errorMessage = videoError.orEmpty(),
                onRetry = {
                    videoError = null
                    isBuffering = true
                    retryAttempt++
                },
                onDownload = { downloadViewModel.downloadItem(item) },
                onOpenExternal = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(streamingUrl), "video/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            "Не найден внешний видеоплеер",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Player control deck.
        AnimatedVisibility(
            visible = showOverlay && videoError == null,
            enter = fadeIn(animationSpec = tween(UiMotionTokens.shortDurationMillis, easing = UiMotionTokens.standardEasing)),
            exit = fadeOut(animationSpec = tween(UiMotionTokens.shortDurationMillis, easing = UiMotionTokens.standardEasing)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(color = Color(0xCC000000)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = UiSpacing.medium, vertical = UiSpacing.small)
                ) {
                    AnimatedVisibility(visible = showMetadata) {
                        MediaMetadataPanel(
                            item = item,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = UiSpacing.small)
                                .testTag("media_viewer_metadata_panel")
                        )
                    }

                    VideoSeekBar(
                        positionMs = if (isSeeking) seekPositionMs else positionMs,
                        bufferedMs = bufferedMs,
                        durationMs = durationMs,
                        enabled = durationMs > 0,
                        onSeekStarted = {
                            isSeeking = true
                            seekPositionMs = positionMs
                        },
                        onSeek = { target ->
                            seekPositionMs = target
                        },
                        onSeekFinished = { target ->
                            player.seekTo(target)
                            positionMs = target
                            isSeeking = false
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimeMs(if (isSeeking) seekPositionMs else positionMs),
                            color = PlayerOnSurface,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = " / ",
                            color = PlayerOnSurface.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = formatTimeMs(durationMs),
                            color = PlayerOnSurface.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.weight(1f))

                        IconButton(onClick = { showMetadata = !showMetadata }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = if (showMetadata) "Скрыть сведения" else "Показать сведения",
                                tint = Color.White
                            )
                        }

                        Box {
                            IconButton(onClick = { speedMenuOpen = true }) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = "Скорость воспроизведения: %.2fx".format(playbackSpeed),
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = speedMenuOpen,
                                onDismissRequest = { speedMenuOpen = false },
                                containerColor = Color(0xF2000000)
                            ) {
                                PLAYBACK_SPEEDS.forEach { speed ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "%.2fx".format(speed) + if (speed == 1f) " (обычная)" else "",
                                                color = if (speed == playbackSpeed) PlayerAccent else Color.White
                                            )
                                        },
                                        onClick = {
                                            player.setPlaybackSpeed(speed)
                                            speedMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(onClick = {
                            val nextMuted = !isMuted
                            player.volume = if (nextMuted) 0f else 1f
                            isMuted = nextMuted
                        }) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = if (isMuted) "Включить звук" else "Выключить звук",
                                tint = Color.White
                            )
                        }

                        IconButton(onClick = {
                            val nextLoop = !isLoopingOne
                            player.repeatMode =
                                if (nextLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            isLoopingOne = nextLoop
                        }) {
                            Icon(
                                imageVector = Icons.Default.Loop,
                                contentDescription = if (isLoopingOne) "Выключить повтор" else "Повторять видео",
                                tint = if (isLoopingOne) PlayerAccent else Color.White
                            )
                        }

                        IconButton(onClick = {
                            val targetActivity = activity ?: return@IconButton
                            isFullscreenRequested = !isFullscreenRequested
                            targetActivity.requestedOrientation =
                                if (isFullscreenRequested) {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                } else {
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                        }) {
                            Icon(
                                imageVector = if (isFullscreenRequested) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreenRequested) "Выйти из полноэкранного режима" else "Полноэкранный режим",
                                tint = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        IconButton(onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "Назад на 10 секунд",
                                tint = Color.White
                            )
                        }

                        Surface(
                            color = PlayerAccent,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            IconButton(onClick = {
                                if (player.isPlaying) player.pause() else player.play()
                            }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                                    tint = Color(0xFF2B1218)
                                )
                            }
                        }

                        IconButton(onClick = {
                            player.seekTo(
                                (player.currentPosition + 10_000L)
                                    .coerceAtMost(player.duration.coerceAtLeast(0L))
                            )
                        }) {
                            Icon(
                                Icons.Default.Forward10,
                                contentDescription = "Вперёд на 10 секунд",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom transport seek bar with a buffered-range indicator.
 *
 * Implemented with Canvas + raw gesture detectors because [androidx.compose.material3.Slider]
 * cannot display secondary (buffered) progress.
 */
@Composable
private fun VideoSeekBar(
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

@Composable
private fun VideoErrorPanel(
    errorMessage: String,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xE6000000),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiSpacing.doubleExtraLarge)
            .testTag("video_error_panel")
    ) {
        Column(
            modifier = Modifier.padding(UiSpacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UiSpacing.medium)
        ) {
            Text(
                text = "Не удалось воспроизвести видео",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "Возможно, формат файла не поддерживается устройством или соединение прервано ($errorMessage).",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text("Повторить")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.medium)) {
                androidx.compose.material3.OutlinedButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(UiSpacing.iconGap))
                    Text("Скачать", color = Color.White)
                }
                androidx.compose.material3.OutlinedButton(onClick = onOpenExternal) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(UiSpacing.iconGap))
                    Text("Внешний плеер", color = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun MediaMetadataPanel(item: CloudItem, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("media_viewer_metadata_panel"),
        color = Color(0xE6000000),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(horizontal = UiSpacing.large, vertical = UiSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)
        ) {
            Text(
                text = if (item.isVideo) "Видео" else "Фото",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Размер · ${item.formattedSize}",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = item.formattedDate.takeIf { it.isNotBlank() }?.let { "Изменено · $it" }
                    ?: "Дата не указана",
                color = Color.White.copy(alpha = 0.86f),
                style = MaterialTheme.typography.bodyMedium
            )
            item.formattedDuration?.let { duration ->
                Text(
                    text = "Длительность · $duration",
                    color = Color.White.copy(alpha = 0.86f),
                    style = MaterialTheme.typography.bodyMedium
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
    viewModel: com.example.ui.viewmodel.CloudLinkViewModel,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            Column(modifier = Modifier.padding(horizontal = UiSpacing.extraLarge, vertical = UiSpacing.large)) {
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
                contentPadding = PaddingValues(horizontal = UiSpacing.small, vertical = UiSpacing.extraSmall),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.extraSmall)
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
                                role = androidx.compose.ui.semantics.Role.Button,
                                onClick = { viewModel.openMediaViewer(mediaItem) }
                            )
                            .padding(UiSpacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(viewModel.getThumbnailUrl(mediaItem.weblink, ThumbnailSize.SMALL))
                                .crossfade(160)
                                .build(),
                            contentDescription = "Предпросмотр: ${mediaItem.name}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(UiSpacing.medium))
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
            CircularProgressIndicator(color = PlayerAccent)
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

