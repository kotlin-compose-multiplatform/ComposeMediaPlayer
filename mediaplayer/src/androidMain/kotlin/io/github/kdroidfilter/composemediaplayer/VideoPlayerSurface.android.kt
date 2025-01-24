package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView

@Composable
actual fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                (playerState).let { state ->
                    player = state.exoPlayer
                    useController = false
                    defaultArtwork = null
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    // Hide the surface when no media is loaded
                    visibility = if (state.hasMedia) android.view.View.VISIBLE else android.view.View.GONE
                }
            }
        },
        update = { view ->
            // Update visibility when hasMedia changes
            view.visibility = if (playerState.hasMedia) android.view.View.VISIBLE else android.view.View.GONE
        }
    )
}