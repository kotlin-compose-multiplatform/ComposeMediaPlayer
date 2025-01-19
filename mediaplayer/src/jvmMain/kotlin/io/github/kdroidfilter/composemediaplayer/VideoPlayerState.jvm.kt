package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Stable
import com.sun.jna.Platform
import io.github.kdroidfilter.composemediaplayer.javafx.JavaFxVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.linux.LinuxVideoPlayerState


@Stable
actual open class VideoPlayerState {
    val delegate: PlatformVideoPlayerState = when {
        Platform.isWindows() || Platform.isMac() -> JavaFxVideoPlayerState()
        Platform.isLinux() -> LinuxVideoPlayerState()
        else -> throw UnsupportedOperationException("Unsupported platform")
    }

    actual open val isPlaying: Boolean get() = delegate.isPlaying
    actual open var volume: Float
        get() = delegate.volume
        set(value) { delegate.volume = value }
    actual open var sliderPos: Float
        get() = delegate.sliderPos
        set(value) { delegate.sliderPos = value }
    actual open var userDragging: Boolean
        get() = delegate.userDragging
        set(value) { delegate.userDragging = value }
    actual open var loop: Boolean
        get() = delegate.loop
        set(value) { delegate.loop = value }
    actual open val leftLevel: Float get() = delegate.leftLevel
    actual open val rightLevel: Float get() = delegate.rightLevel
    actual open val positionText: String get() = delegate.positionText
    actual open val durationText: String get() = delegate.durationText

    actual open fun openUri(uri: String) = delegate.openUri(uri)
    actual open fun play() = delegate.play()
    actual open fun pause() = delegate.pause()
    actual open fun stop() = delegate.stop()
    actual open fun seekTo(value: Float) = delegate.seekTo(value)
    actual open fun dispose() = delegate.dispose()
}