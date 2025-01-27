package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import kotlin.math.abs

@Composable
actual fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier) {
    var videoElement by remember { mutableStateOf<HTMLVideoElement?>(null) }
    val scope = rememberCoroutineScope()

    // Create the HTML video element
    HtmlView(
        factory = {
            createVideoElement()
        },
        modifier = modifier,
        update = { video ->
            videoElement = video
            setupVideoElement(video, playerState, scope)
        }
    )

    // Handle source URI effects
    LaunchedEffect(playerState.sourceUri) {
        videoElement?.let {
            it.src = playerState.sourceUri ?: ""
            if (playerState.isPlaying) {
                it.play()
            } else {
                it.pause()
            }
        }
    }

    // Handle playback effects
    LaunchedEffect(playerState.isPlaying) {
        videoElement?.let {
            if (playerState.isPlaying) {
                it.play()
            } else {
                it.pause()
            }
        }
    }

    // Handle volume effects
    LaunchedEffect(playerState.volume) {
        videoElement?.volume = playerState.volume.toDouble()
    }

    // Handle loop effects
    LaunchedEffect(playerState.loop) {
        videoElement?.loop = playerState.loop
    }

    // Handle seek effects via sliderPos with debounce
    LaunchedEffect(playerState.sliderPos) {
        if (!playerState.userDragging && playerState.hasMedia) {
            val job = scope.launch {
                val duration = videoElement?.duration?.toFloat() ?: 0f
                if (duration > 0f) {
                    val newTime = (playerState.sliderPos / VideoPlayerState.PERCENTAGE_MULTIPLIER) * duration
                    // Avoid updating if the difference is too small
                    if (abs((videoElement?.currentTime ?: 0.0) - newTime) > 0.5) {
                        videoElement?.currentTime = newTime.toDouble()
                    }
                }
            }
            // Cancel the previous job if a new sliderPos is received before the delay
            playerState.seekJob?.cancel()
            playerState.seekJob = job
        }
    }
}

private fun createVideoElement(): HTMLVideoElement {
    return (document.createElement("video") as HTMLVideoElement).apply {
        controls = false
        style.width = "100%"
        style.height = "100%"
    }
}

private fun setupVideoElement(
    video: HTMLVideoElement,
    playerState: VideoPlayerState,
    scope: CoroutineScope
) {
    // Clean up old listeners
    video.removeEventListener("timeupdate", playerState::onTimeUpdateEvent)
    video.addEventListener("timeupdate", playerState::onTimeUpdateEvent)

    // Event triggered when the video starts a seek operation
    video.addEventListener("seeking", {
        scope.launch {
            playerState._isLoading = true
        }
    })

    // Event triggered when the seek operation is complete
    video.addEventListener("seeked", {
        scope.launch {
            playerState._isLoading = false
        }
    })

    // Event triggered when the video is waiting for data (buffering)
    video.addEventListener("waiting", {
        scope.launch {
            playerState._isLoading = true
        }
    })

    // Event triggered when the video can start or resume playback
    video.addEventListener("playing", {
        scope.launch {
            playerState._isLoading = false
        }
    })

    // Event triggered when the video can play through without interruption
    video.addEventListener("canplaythrough", {
        scope.launch {
            playerState._isLoading = false
        }
    })

    // Event for "can play" state
    video.addEventListener("canplay", {
        scope.launch {
            playerState._isLoading = false
        }
    })

    video.addEventListener("suspend", {
        scope.launch {
            // Only set isLoading to false if we have enough data to play
            if (video.readyState >= 3) { // HAVE_FUTURE_DATA or better
                playerState._isLoading = false
            }
        }
    })

    // Listener for playback errors
    video.addEventListener("error", {
        scope.launch {
            playerState._isLoading = false
        }
    })

    // Listener for metadata
    video.addEventListener("loadedmetadata", {
        scope.launch {
            playerState._isLoading = false
            if (playerState.isPlaying) {
                try {
                    video.play()
                } catch (e: Exception) {
                    println("Error opening media: ${e.message}")
                }
            }
        }
    })

    // Apply volume, loop, etc.
    video.volume = playerState.volume.toDouble()
    video.loop = playerState.loop

    // Play if the source is set and state allows
    if (video.src.isNotEmpty() && playerState.isPlaying) {
        try {
            video.play()
        } catch (e: Exception) {
            println("Error opening media: ${e.message}")
        }
    }
}

// Add an extension to handle the "timeupdate" event
private fun VideoPlayerState.onTimeUpdateEvent(event: Event) {
    val video = event.target as? HTMLVideoElement
    video?.let {
        onTimeUpdate(it.currentTime.toFloat(), it.duration.toFloat())
    }
}
