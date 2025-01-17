package io.github.kdroidfilter.composemediaplayer

import android.util.Log
import androidx.compose.runtime.Stable

@Stable
actual class VideoPlayerState {
    //TODO

    actual val isPlaying: Boolean = false
    actual var volume: Float = 1.0f
    actual var sliderPos: Float = 0.0f
    actual var userDragging: Boolean = false
    actual var loop: Boolean = false
    actual val leftLevel: Float = 0.0f
    actual val rightLevel: Float = 0.0f
    actual val positionText: String = "00:00"
    actual val durationText: String = "00:00"

    actual fun openUri(uri: Any) {
        Log.d("VideoPlayerState", "openUri called with URI: $uri")
    }

    actual fun play() {
        Log.d("VideoPlayerState", "play called")
    }

    actual fun pause() {
        Log.d("VideoPlayerState", "pause called")
    }

    actual fun stop() {
        Log.d("VideoPlayerState", "stop called")
    }

    actual fun seekTo(value: Float) {
        Log.d("VideoPlayerState", "seekTo called with value: $value")
    }

    actual fun dispose() {
        Log.d("VideoPlayerState", "dispose called")
    }
}
