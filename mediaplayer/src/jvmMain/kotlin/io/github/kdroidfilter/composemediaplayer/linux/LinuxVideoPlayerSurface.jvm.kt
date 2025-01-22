package io.github.kdroidfilter.composemediaplayer.linux

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel

/**
 * A composable function that renders a video player surface using GStreamer.
 *
 * This function creates a video rendering area by embedding a Swing-based GStreamer video component
 * within a Jetpack Compose UI. The rendering is controlled through the provided `VideoPlayerState`.
 *
 * @param state The state object (`VideoPlayerState`) that encapsulates the GStreamer player logic,
 *              including playback control, timeline management, and video interaction.
 * @param modifier An optional `Modifier` for customizing the layout and appearance of the
 *                 composable container. Defaults to an empty `Modifier`.
 */

@Composable
fun LinuxVideoPlayerSurface(
    playerState: LinuxVideoPlayerState,
    modifier: Modifier,
) {
    Box(modifier = modifier) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = { playerState.gstVideoComponent },
        )
    }
}