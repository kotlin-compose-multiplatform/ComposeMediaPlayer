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
internal fun WindowsVideoPlayerSurface(
    playerState: WindowsVideoPlayerState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val swingPanelModifier = if(!playerState.hasMedia) Modifier else Modifier.fillMaxHeight().aspectRatio(playerState.aspectRatio)
        SwingPanel(
            modifier = swingPanelModifier,
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
