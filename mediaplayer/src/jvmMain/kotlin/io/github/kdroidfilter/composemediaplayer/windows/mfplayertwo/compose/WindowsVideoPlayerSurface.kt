package io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.ui.VideoCanvas
import kotlinx.coroutines.delay
import java.awt.Color
import javax.swing.SwingUtilities

@Composable
fun WindowsVideoPlayerSurface(
    playerState: WindowsVideoPlayerState,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                VideoCanvas().apply {
                    background = Color.BLACK
                    SwingUtilities.invokeLater {
                        if (!playerState.isInitialized) {
                            playerState.initializeWithCanvas(this)
                        }
                    }
                }
            }
        )

        LaunchedEffect(playerState.isPlaying, playerState.isInitialized) {
            while (playerState.isInitialized && playerState.isPlaying) {
                playerState.updateVideo()
                delay(16)
            }
        }
    }
}