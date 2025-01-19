package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.composemediaplayer.javafx.JavaFxVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.javafx.JavaFxVideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.linux.LinuxVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.linux.LinuxVideoPlayerSurface

@Composable
actual fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier) {
    when (val delegate = playerState.delegate) {
        is JavaFxVideoPlayerState -> JavaFxVideoPlayerSurface(delegate, modifier)
        is LinuxVideoPlayerState -> LinuxVideoPlayerSurface(delegate, modifier)
        else -> throw IllegalArgumentException("Unsupported player state type")
    }
}