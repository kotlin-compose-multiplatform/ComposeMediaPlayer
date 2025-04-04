package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders a video player surface that displays and controls video playback.
 *
 * @param playerState The state of the video player, which manages playback controls,
 *                    video position, volume, and other related properties.
 * @param modifier    The modifier to be applied to the video player surface for
 *                    layout and styling adjustments.
 */
@Composable
expect fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier)
