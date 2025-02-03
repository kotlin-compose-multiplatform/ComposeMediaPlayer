package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.composemediaplayer.htmlinterop.HtmlView
import io.github.kdroidfilter.composemediaplayer.util.logger
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLTrackElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import kotlin.math.abs


@Composable
actual fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier) {
    if (playerState.hasMedia) {

        var videoElement by remember { mutableStateOf<HTMLVideoElement?>(null) }
        val scope = rememberCoroutineScope()

        // Create HTML video element
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

        // Handle source change effect
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

        // Handle play/pause
        LaunchedEffect(playerState.isPlaying) {
            videoElement?.let {
                if (playerState.isPlaying) {
                    it.play()
                } else {
                    it.pause()
                }
            }
        }

        // Handle volume update
        LaunchedEffect(playerState.volume) {
            videoElement?.volume = playerState.volume.toDouble()
        }

        // Handle loop update
        LaunchedEffect(playerState.loop) {
            videoElement?.loop = playerState.loop
        }

        // Handle seek via sliderPos (with debounce)
        LaunchedEffect(playerState.sliderPos) {
            if (!playerState.userDragging && playerState.hasMedia) {
                val job = scope.launch {
                    val duration = videoElement?.duration?.toFloat() ?: 0f
                    if (duration > 0f) {
                        val newTime = (playerState.sliderPos / VideoPlayerState.PERCENTAGE_MULTIPLIER) * duration
                        // Avoid seeking if the difference is small
                        if (abs((videoElement?.currentTime ?: 0.0) - newTime) > 0.5) {
                            videoElement?.currentTime = newTime.toDouble()
                        }
                    }
                }
                // Cancel previous job if a new sliderPos arrives before completion
                playerState.seekJob?.cancel()
                playerState.seekJob = job
            }
        }

        LaunchedEffect(playerState.currentSubtitleTrack) {
            videoElement?.let { video ->
                val trackElements = video.querySelectorAll("track")
                for (i in 0 until trackElements.length) {
                    val track = trackElements.item(i)
                    track?.let { video.removeChild(it) }
                }

                playerState.currentSubtitleTrack?.let { track ->
                    val trackElement = document.createElement("track") as HTMLTrackElement
                    trackElement.kind = "subtitles"
                    trackElement.label = track.label
                    trackElement.srclang = track.language
                    trackElement.src = track.src
                    trackElement.default = true
                    video.appendChild(trackElement)
                }
            }
        }
    }
}

private fun createVideoElement(): HTMLVideoElement {
    return (document.createElement("video") as HTMLVideoElement).apply {
        controls = false
        style.width = "100%"
        style.height = "100%"
        crossOrigin = "anonymous"
    }
}

/**
 * Configure video element: listeners, WebAudioAnalyzer, etc.
 */
fun setupVideoElement(
    video: HTMLVideoElement,
    playerState: VideoPlayerState,
    scope: CoroutineScope,
    enableAudioDetection: Boolean = true
) {
    logger.debug { "Setup video => enableAudioDetection = $enableAudioDetection" }

    // Create analyzer only if enableAudioDetection is true
    val audioAnalyzer = if (enableAudioDetection) {
        AudioLevelProcessor(video)
    } else null

    var initializationJob: Job? = null

    // Helper => initialize analysis if enableAudioDetection
    fun initAudioAnalyzer() {
        if (!enableAudioDetection) return
        initializationJob?.cancel()
        initializationJob = scope.launch {
            audioAnalyzer?.initialize()
        }
    }

    // loadedmetadata => attempt initialization
    video.addEventListener("loadedmetadata") {
        logger.debug { "Video => loadedmetadata => init analyzer if enabled" }
        initAudioAnalyzer()
    }

    // play => re-init
    video.addEventListener("play") {
        logger.debug { "Video => play => init analyzer if needed" }

        if (!enableAudioDetection) {
            logger.debug { "Audio detection disabled => no analyzer." }
        } else if (initializationJob?.isActive != true) {
            initAudioAnalyzer()
        }

        // Loop => read levels only if analyzer is not null
        if (enableAudioDetection) {
            scope.launch {
                logger.debug { "Starting audio level update loop" }
                while (true) {
                    val (left, right) = audioAnalyzer?.getAudioLevels() ?: (0f to 0f)
                    playerState.updateAudioLevels(left, right)
                    delay(100)
                }
            }
        }
    }

    // timeupdate
    video.removeEventListener("timeupdate", playerState::onTimeUpdateEvent)
    video.addEventListener("timeupdate", playerState::onTimeUpdateEvent)

    // seeking, waiting, canplay, etc.
    video.addEventListener("seeking") {
        scope.launch { playerState._isLoading = true }
    }
    video.addEventListener("seeked") {
        scope.launch { playerState._isLoading = false }
    }
    video.addEventListener("waiting") {
        scope.launch { playerState._isLoading = true }
    }
    video.addEventListener("playing") {
        scope.launch { playerState._isLoading = false }
    }
    video.addEventListener("canplaythrough") {
        scope.launch { playerState._isLoading = false }
    }
    video.addEventListener("canplay") {
        scope.launch { playerState._isLoading = false }
    }
    video.addEventListener("suspend") {
        scope.launch {
            if (video.readyState >= 3) {
                playerState._isLoading = false
            }
        }
    }
    // error
    video.addEventListener("error") {
        scope.launch {
            playerState._isLoading = false
            logger.error { "Video => error => possibly no audio analyzer if CORS issues." }
        }
    }

    // loadedmetadata => set isLoading false + play if needed
    video.addEventListener("loadedmetadata") {
        scope.launch {
            playerState._isLoading = false
            if (playerState.isPlaying) {
                try {
                    video.play()
                } catch (e: Exception) {
                    logger.error(e) { "Error opening media: ${e.message}" }
                }
            }
        }
    }

    // volume, loop
    video.volume = playerState.volume.toDouble()
    video.loop = playerState.loop

    // If source already exists + want to play
    if (video.src.isNotEmpty() && playerState.isPlaying) {
        try {
            video.play()
        } catch (e: Exception) {
            logger.error(e) { "Error opening media: ${e.message}" }
        }
    }
}

// Handle "timeupdate" event to manage progress cursor
private fun VideoPlayerState.onTimeUpdateEvent(event: Event) {
    val video = event.target as? HTMLVideoElement
    video?.let {
        onTimeUpdate(it.currentTime.toFloat(), it.duration.toFloat())
    }
}

