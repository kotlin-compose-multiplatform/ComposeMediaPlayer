package io.github.kdroidfilter.composemediaplayer.windows.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import io.github.kdroidfilter.composemediaplayer.windows.ui.VideoCanvas
import javax.swing.SwingUtilities


@Composable
fun WindowsVideoPlayerSurface(
    playerState: WindowsVideoPlayerState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        SwingPanel(
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(16f/9f), // Force 16:9 aspect ratio
            factory = {
                VideoCanvas().apply {
                    SwingUtilities.invokeLater {
                        if (!playerState.isInitialized) {
                            playerState.initializeWithCanvas(this)
                        }
                    }
                }
            }
        )
    }
}