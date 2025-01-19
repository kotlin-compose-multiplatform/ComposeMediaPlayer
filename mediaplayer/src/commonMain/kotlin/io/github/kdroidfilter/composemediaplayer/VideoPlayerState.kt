package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember

@Stable
expect open class VideoPlayerState() {
    val isPlaying: Boolean
    var volume: Float

    var sliderPos: Float
    var userDragging: Boolean
    var loop: Boolean
    val leftLevel: Float
    val rightLevel: Float
    val positionText: String
    val durationText: String

    fun openUri(uri: String)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(value: Float)
    fun dispose()

}

@Composable
fun rememberVideoPlayerState(): VideoPlayerState {
    val playerState = remember { VideoPlayerState() }
    DisposableEffect(Unit) {
        onDispose {
            playerState.dispose()
        }
    }
    return playerState
}