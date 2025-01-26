package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLVideoElement

// Constants
private const val UPDATE_DELAY = 100L
private const val PERCENTAGE_MULTIPLIER = 1000f

@Composable
actual fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier) {
    var videoElement by remember { mutableStateOf<HTMLVideoElement?>(null) }
    val scope = rememberCoroutineScope()

    HtmlView(
        factory = {
            createVideoElement()
        },
        modifier = modifier,
        update = { video ->
            videoElement = video
            updateVideoElement(video, playerState, scope)
        }
    )

    LaunchedEffect(playerState.sourceUri) {
        videoElement?.updateSourceUri(playerState)
    }

    LaunchedEffect(playerState.isPlaying) {
        videoElement?.setPlayingState(playerState.isPlaying)
    }

    LaunchedEffect(playerState.volume) {
        videoElement?.volume = playerState.volume.toDouble()
    }

    LaunchedEffect(playerState.loop) {
        videoElement?.loop = playerState.loop
    }

    LaunchedEffect(playerState.sliderPos) {
        videoElement?.updateSliderPosition(playerState)
    }
}

private fun createVideoElement(): HTMLVideoElement {
    return (document.createElement("video") as HTMLVideoElement).apply {
        controls = false
        style.width = "100%"
        style.height = "100%"
    }
}

private fun updateVideoElement(
    video: HTMLVideoElement,
    playerState: VideoPlayerState,
    scope: CoroutineScope
) {
    if (playerState.hasMedia) {
        playerState.sourceUri?.let { uri ->
            if (video.src != uri) {
                video.src = uri
                if (playerState.isPlaying) {
                    video.play()
                }
            }
        }
    } else {
        video.pause()
        video.src = ""
    }

    video.onwaiting = {
        // Handle waiting state if needed
    }

    video.oncanplaythrough = {
        // Handle can play through state if needed
    }

    video.onplaying = {
        // Handle playing state if needed
    }

    if (playerState.hasMedia) {
        video.ontimeupdate = {
            scope.launch {
                delay(UPDATE_DELAY)
                playerState.updatePosition(
                    video.currentTime.toFloat(),
                    video.duration.toFloat()
                )
            }
        }
    }
}

private fun HTMLVideoElement.updateSourceUri(playerState: VideoPlayerState) {
    val uri = playerState.sourceUri
    this.src = uri ?: ""
    if (uri != null && playerState.isPlaying) {
        this.play()
    } else {
        this.pause()
    }
}

private fun HTMLVideoElement.setPlayingState(isPlaying: Boolean) {
    if (isPlaying) this.play() else this.pause()
}

private fun HTMLVideoElement.updateSliderPosition(playerState: VideoPlayerState) {
    if (!playerState.userDragging && playerState.hasMedia) {
        val duration = this.duration.toFloat()
        val newTime = (playerState.sliderPos / PERCENTAGE_MULTIPLIER) * duration
        this.currentTime = newTime.toDouble()
    }
}