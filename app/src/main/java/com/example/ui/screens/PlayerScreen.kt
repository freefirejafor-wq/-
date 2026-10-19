package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.model.MatchFixture
import com.example.model.MatchStatus
import com.example.model.StreamFormat
import com.example.model.StreamSource
import com.example.ui.components.FixtureCard
import com.example.ui.components.TeamLogoBadge
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanPillBg
import com.example.ui.theme.LiveRed
import com.example.ui.theme.NavyBackground
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    fixture: MatchFixture,
    initialStream: StreamSource,
    relatedFixtures: List<MatchFixture>,
    favoriteIds: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onBackClick: () -> Unit,
    onFixtureClick: (MatchFixture) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var currentStream by remember(fixture, initialStream) { mutableStateOf(initialStream) }
    var isPlaying by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    var isControlsLocked by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderProgress by remember { mutableFloatStateOf(0f) }

    // ExoPlayer creation & lifecycle
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Load stream source
    fun prepareMedia(stream: StreamSource) {
        isLoading = true
        errorMessage = null
        try {
            val uri = Uri.parse(stream.streamUrl)
            val dataSourceFactory = DefaultDataSource.Factory(context)

            val mediaSource: MediaSource = when (stream.format) {
                StreamFormat.DASH -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
                StreamFormat.HLS -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
                StreamFormat.PROGRESSIVE -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
            }

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.play()
        } catch (e: Exception) {
            errorMessage = "Invalid stream link or codec error: ${e.message}"
            isLoading = false
        }
    }

    LaunchedEffect(currentStream) {
        prepareMedia(currentStream)
    }

    // Player event listener
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = (playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_READY) {
                    errorMessage = null
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                isLoading = false
                errorMessage = "Playback Error: ${error.errorCodeName} (Tap Retry)"
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Periodic time progress updater
    LaunchedEffect(isPlaying, isSeeking) {
        while (isPlaying && !isSeeking) {
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (durationMs > 0) {
                sliderProgress = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }
            delay(500)
        }
    }

    // Auto-hide controls after 4 seconds
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && !isControlsLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Back handler
    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            onBackClick()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyBackground)
            .testTag("screen_player")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Video Player Box (Full width, 16:9 in portrait, fillMaxSize in fullscreen)
            Box(
                modifier = if (isFullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                }
            ) {
                // ExoPlayer Surface
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            this.resizeMode = resizeMode
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                        playerView.resizeMode = resizeMode
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable {
                            if (!isControlsLocked) {
                                showControls = !showControls
                            } else {
                                showControls = true // allow tapping lock icon to unlock
                            }
                        }
                )

                // Loading Indicator
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = CyanAccent,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                // Error State Overlay
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage ?: "Unable to stream channel",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { prepareMedia(currentStream) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Stream", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Custom On-Screen Player Controls (matching screenshots!)
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(12.dp)
                    ) {
                        // Top Bar: Back arrow, Title, Volume, Lock
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (isFullscreen) {
                                            isFullscreen = false
                                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        } else {
                                            onBackClick()
                                        }
                                    },
                                    modifier = Modifier.testTag("btn_player_back")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                                Text(
                                    text = fixture.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        exoPlayer.volume = if (isMuted) 0f else 1f
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = Color.White
                                    )
                                }

                                IconButton(
                                    onClick = { isControlsLocked = !isControlsLocked }
                                ) {
                                    Icon(
                                        imageVector = if (isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Lock Controls",
                                        tint = if (isControlsLocked) CyanAccent else Color.White
                                    )
                                }
                            }
                        }

                        // Middle Controls: Rewind 10s, Play/Pause, Forward 10s, Aspect Ratio, PiP
                        if (!isControlsLocked) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Aspect ratio toggle
                                IconButton(
                                    onClick = {
                                        resizeMode = when (resizeMode) {
                                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AspectRatio,
                                        contentDescription = "Aspect Ratio",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Picture in picture / Floating player
                                IconButton(
                                    onClick = {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            activity?.enterPictureInPictureMode()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureInPictureAlt,
                                        contentDescription = "Floating Player",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Rewind 10s
                                IconButton(
                                    onClick = {
                                        val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                        exoPlayer.seekTo(newPos)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastRewind,
                                        contentDescription = "Rewind 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                // Play / Pause
                                Box(
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.25f))
                                        .clickable {
                                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                        }
                                        .testTag("btn_player_play_pause"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                // Forward 10s
                                IconButton(
                                    onClick = {
                                        val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(durationMs)
                                        exoPlayer.seekTo(newPos)
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FastForward,
                                        contentDescription = "Forward 10s",
                                        tint = Color.White,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                // Settings
                                IconButton(
                                    onClick = {}
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Fullscreen Toggle
                                IconButton(
                                    onClick = {
                                        isFullscreen = !isFullscreen
                                        activity?.requestedOrientation = if (isFullscreen) {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        } else {
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        }
                                    },
                                    modifier = Modifier.testTag("btn_player_fullscreen")
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }

                        // Bottom Scrubber Bar (matching screenshot: 59:29 ---●--- 1:00:00)
                        if (!isControlsLocked) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDuration(currentPositionMs),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Slider(
                                    value = sliderProgress,
                                    onValueChange = {
                                        isSeeking = true
                                        sliderProgress = it
                                    },
                                    onValueChangeFinished = {
                                        isSeeking = false
                                        val target = (sliderProgress * durationMs).toLong()
                                        exoPlayer.seekTo(target)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = CyanAccent,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )

                                Text(
                                    text = if (durationMs > 0) formatDuration(durationMs) else "LIVE",
                                    color = if (durationMs > 0) Color.White else LiveRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Below Player: Stream pills, match header, and fixture cards
            if (!isFullscreen) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Available Stream Buttons Row (matching screenshot!)
                    item {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(fixture.streams) { stream ->
                                val isSelected = stream.id == currentStream.id
                                val pillShape = RoundedCornerShape(20.dp)

                                Row(
                                    modifier = Modifier
                                        .clip(pillShape)
                                        .background(if (isSelected) CyanPillBg else NavyCard)
                                        .border(
                                            width = 1.2.dp,
                                            color = if (isSelected) CyanAccent else NavyCardBorder,
                                            shape = pillShape
                                        )
                                        .clickable {
                                            currentStream = stream
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .testTag("stream_pill_${stream.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Playing",
                                            tint = CyanAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = stream.name,
                                        color = if (isSelected) Color.White else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Live Match Header Bar (matching screenshot: Team1 Logo | Live | Team2 Logo | divider)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            TeamLogoBadge(name = fixture.team1Name, abbr = fixture.team1Abbr)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(LiveRed)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Live",
                                        color = LiveRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (fixture.team1Score != null && fixture.team2Score != null) {
                                    Text(
                                        text = "${fixture.team1Score} - ${fixture.team2Score}",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            TeamLogoBadge(name = fixture.team2Name, abbr = fixture.team2Abbr)

                            // Divider dot
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.6f))
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Fixture Cards below
                    items(relatedFixtures) { otherFixture ->
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            FixtureCard(
                                fixture = otherFixture,
                                isFavorite = favoriteIds.contains(otherFixture.id),
                                onCardClick = { onFixtureClick(otherFixture) },
                                onToggleFavorite = { onToggleFavorite(otherFixture.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
