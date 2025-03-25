package io.github.kdroidfilter.composemediaplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import kotlinx.cinterop.ExperimentalForeignApi


@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VideoPlayerSurface(
    playerState: VideoPlayerState,
    modifier: Modifier
) {
    Box(modifier = modifier)
    val frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 640.0, 360.0)
    val playerView = platform.AVKit.AVPlayerView(frame = frame).apply {
        wantsLayer = true
        autoresizesSubviews = true
        player = playerState.player
    }

}
