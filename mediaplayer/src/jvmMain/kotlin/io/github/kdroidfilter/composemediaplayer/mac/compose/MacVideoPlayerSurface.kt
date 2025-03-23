package io.github.kdroidfilter.composemediaplayer.mac.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale

/**
 * A Composable function that renders a video player surface for MacOS.
 * Properly scales the video to fit within its container while maintaining aspect ratio.
 *
 * @param playerState The state object that encapsulates the AVPlayer logic for MacOS.
 * @param modifier An optional Modifier for customizing the layout.
 * @param backgroundColor Background color to show when no video is displayed.
 */
@Composable
fun MacVideoPlayerSurface(
    playerState: MacVideoPlayerState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (playerState.hasMedia) {
            val currentFrame by playerState.currentFrameState
            val aspectRatio = playerState.aspectRatio

            currentFrame?.let { frame ->
                    // Calculate scaling factors based on container size and video aspect ratio
                    Canvas(
                        modifier = Modifier.fillMaxHeight()
                            .aspectRatio(playerState.aspectRatio),
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        // Calculate the appropriate scaling factors to maintain aspect ratio
                        val imageAspect = aspectRatio
                        val canvasAspect = canvasWidth / canvasHeight

                        val scaleX: Float
                        val scaleY: Float

                        if (imageAspect > canvasAspect) {
                            // Image is wider than canvas - fit to width
                            scaleX = canvasWidth / frame.width
                            scaleY = scaleX
                        } else {
                            // Image is taller than canvas - fit to height
                            scaleY = canvasHeight / frame.height
                            scaleX = scaleY
                        }

                        // Calculate centering offsets
                        val scaledImageWidth = frame.width * scaleX
                        val scaledImageHeight = frame.height * scaleY
                        val offsetX = (canvasWidth - scaledImageWidth) / 2
                        val offsetY = (canvasHeight - scaledImageHeight) / 2

                        // Draw the scaled and centered image
                        scale(scaleX, scaleY, Offset(offsetX, offsetY)) {
                            drawImage(
                                image = frame,
                                topLeft = Offset(offsetX / scaleX, offsetY / scaleY)
                            )
                        }
                    }
                }
            }

    }
}