package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.PlatformFile

/**
 * Represents the state and controls for a video player. This class provides properties
 * and methods to manage video playback, including play, pause, stop, seeking, and more.
 * It maintains information about the playback state, such as whether the video is
 * currently playing, volume levels, and playback position.
 *
 * Functions of this class are tied to managing and interacting with the underlying
 * video player implementation.
 *
 * @constructor Initializes an instance of the video player state.
 */
@Stable
expect open class VideoPlayerState() {
    val hasMedia : Boolean
    val isPlaying: Boolean
    var volume: Float
    var sliderPos: Float
    var userDragging: Boolean
    var loop: Boolean
    val leftLevel: Float
    val rightLevel: Float
    val positionText: String
    val durationText: String
    val isLoading: Boolean

    val error: VideoPlayerError?

    val metadata: VideoMetadata

    fun openUri(uri: String)
    fun openFile(file: PlatformFile)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(value: Float)
    fun dispose()
    fun clearError()
}

/**
 * Creates and manages an instance of `VideoPlayerState` within a composable function, ensuring
 * proper disposal of the player state when the composable leaves the composition. This function
 * is used to remember the video player state throughout the composition lifecycle.
 *
 * @return The remembered instance of `VideoPlayerState`, which provides functionalities for
 *         controlling and managing video playback, such as play, pause, stop, and seek.
 */
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