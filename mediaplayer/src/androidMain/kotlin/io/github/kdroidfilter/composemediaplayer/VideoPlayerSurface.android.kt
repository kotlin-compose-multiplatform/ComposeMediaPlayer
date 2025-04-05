package io.github.kdroidfilter.composemediaplayer

import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView

@UnstableApi
@Composable
actual fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            // Create PlayerView with subtitles support
            PlayerView(context).apply {
                // Attach the player from the state
                player = playerState.exoPlayer
                useController = false
                defaultArtwork = null
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)

                // Configure subtitle view
                subtitleView?.apply {
                    setStyle(CaptionStyleCompat.DEFAULT)
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, 18f) //todo let user change subtitle size
                }

                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM

                // Hide the view when no media is loaded
                visibility = if (playerState.hasMedia) View.VISIBLE else View.GONE

                // Attach this view to the player state
                playerState.attachPlayerView(this)
            }
        },
        update = { playerView ->
            playerView.visibility = if (playerState.hasMedia) View.VISIBLE else View.GONE
        }
    )
}