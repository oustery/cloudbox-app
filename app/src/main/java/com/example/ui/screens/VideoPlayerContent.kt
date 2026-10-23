package com.example.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.OptIn as AndroidXOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import com.example.ui.components.expressive.WavyCircularProgress
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CloudItem
import com.example.data.repository.ThumbnailSize
import com.example.ui.theme.UiMotionTokens
import com.example.ui.theme.UiSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
@AndroidXOptIn(UnstableApi::class)
@Composable
internal fun VideoPlayerContent(
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
            // Material 3 Expressive circular wavy buffering indicator.
            WavyCircularProgress(
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

