package io.github.kdroidfilter.composemediaplayer.mac.avplayer

import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.sun.jna.Pointer
import io.github.kdroidfilter.composemediaplayer.PlatformVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.SubtitleTrack
import io.github.kdroidfilter.composemediaplayer.VideoMetadata
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.sharedbuffer.SharedVideoPlayer
import io.github.kdroidfilter.composemediaplayer.util.formatTime
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import kotlin.math.abs
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Logger.Companion.setMinSeverity
import co.touchlab.kermit.Severity

// Initialize logger using Kermit
internal val macLogger = Logger.withTag("MacVideoPlayerState").apply { setMinSeverity(Severity.Warn) }

/**
 * MacVideoPlayerState handles the native Mac video player state.
 *
 * This implementation uses a native video player via SharedVideoPlayer.
 * All debug logs are handled with Kermit.
 */
class MacVideoPlayerState : PlatformVideoPlayerState {

    private var playerPtr: Pointer? = null
    private var _currentFrameState = mutableStateOf<ImageBitmap?>(null)
    internal val currentFrameState: State<ImageBitmap?> = _currentFrameState
    private var frameUpdateJob: Job? = null
    private var bufferedImage: BufferedImage? = null

    override var hasMedia: Boolean by mutableStateOf(false)
    override var isPlaying: Boolean by mutableStateOf(false)

    private var _volume by mutableStateOf(1.0f)
    override var volume: Float
        get() = _volume
        set(value) {
            _volume = value.coerceIn(0f, 1f)
            macLogger.d { "Volume changed to $_volume" }
            playerPtr?.let {
                macLogger.d { "Calling setVolume on native player" }
                SharedVideoPlayer.INSTANCE.setVolume(it, _volume)
            }
        }

    private var seekInProgress = false
    private var targetSeekTime: Double? = null

    override var sliderPos: Float by mutableStateOf(0.0f)
    override var userDragging: Boolean by mutableStateOf(false)
    override var loop: Boolean by mutableStateOf(false)
    override val leftLevel: Float by mutableStateOf(0.0f)
    override val rightLevel: Float by mutableStateOf(0.0f)
    override var isLoading: Boolean by mutableStateOf(false)
    override var error: VideoPlayerError? by mutableStateOf(null)

    override val positionText: String
        get() = formatTime(getCurrentTime())
    override val durationText: String
        get() = formatTime(getDuration())

    internal val aspectRatio: Float
        get() {
            val width = playerPtr?.let { SharedVideoPlayer.INSTANCE.getFrameWidth(it) } ?: 16
            val height = playerPtr?.let { SharedVideoPlayer.INSTANCE.getFrameHeight(it) } ?: 9
            macLogger.d { "Calculating aspectRatio: width=$width, height=$height" }
            return if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 16f / 9f
        }

    override val metadata: VideoMetadata = VideoMetadata()
    override var subtitlesEnabled: Boolean by mutableStateOf(false)
    override var currentSubtitleTrack: SubtitleTrack? by mutableStateOf(null)
    override val availableSubtitleTracks: MutableList<SubtitleTrack> = mutableListOf()

    private var videoFrameRate: Float = 0.0f
    private var screenRefreshRate: Float = 0.0f
    private var captureFrameRate: Float = 0.0f

    private val updateInterval: Long
        get() = if (captureFrameRate > 0) (1000.0f / captureFrameRate).toLong() else 33L

    private var playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        macLogger.d { "Initializing video player" }
        initPlayer()
    }

    /**
     * Initializes the native video player.
     */
    private fun initPlayer() {
        macLogger.d { "initPlayer() - Creating native player" }
        playerPtr = SharedVideoPlayer.INSTANCE.createVideoPlayer().also { ptr ->
            if (ptr != null) {
                macLogger.d { "Native player created successfully" }
                SharedVideoPlayer.INSTANCE.setVolume(ptr, _volume)
            } else {
                macLogger.e { "Error: Failed to create native player" }
                error = VideoPlayerError.UnknownError("Failed to create native player")
            }
        }
    }

    /**
     * Updates the frame rate information from the native player.
     */
    private fun updateFrameRateInfo() {
        macLogger.d { "updateFrameRateInfo()" }
        playerPtr?.let {
            videoFrameRate = SharedVideoPlayer.INSTANCE.getVideoFrameRate(it)
            screenRefreshRate = SharedVideoPlayer.INSTANCE.getScreenRefreshRate(it)
            captureFrameRate = SharedVideoPlayer.INSTANCE.getCaptureFrameRate(it)
            macLogger.d { "Frame Rates - Video: $videoFrameRate, Screen: $screenRefreshRate, Capture: $captureFrameRate" }
        } ?: macLogger.d { "updateFrameRateInfo() - playerPtr is null" }
    }

    override fun openUri(uri: String) {
        macLogger.d { "openUri() - Opening URI: $uri" }
        // Recreate playerScope if it has been cancelled
        if (!playerScope.isActive) {
            macLogger.d { "Recreating a new playerScope" }
            playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        }
        playerScope.launch {
            try {
                isLoading = true

                // First, properly stop any current playback
                if (hasMedia) {
                    pause()  // Stop the current audio playback
                    stopFrameUpdates()

                    // Dispose the current player instance
                    playerPtr?.let {
                        macLogger.d { "disposing previous player before opening new URI" }
                        SharedVideoPlayer.INSTANCE.disposeVideoPlayer(it)
                        playerPtr = null
                    }
                }

                // Now initialize a new player
                initPlayer()

                withContext(Dispatchers.IO) {
                    SharedVideoPlayer.INSTANCE.openUri(playerPtr, uri)
                }

                delay(1000)
                updateFrameRateInfo()
                updateMetadata()

                hasMedia = true
                isLoading = false
                isPlaying = true
                startFrameUpdates()
                updateFrame()

                if (isPlaying) {
                    play()
                }
            } catch (e: Exception) {
                macLogger.e { "openUri() - Exception: ${e.message}" }
                handleError(e)
            }
        }
    }

    /**
     * Updates the metadata from the native player.
     */
    private fun updateMetadata() {
        macLogger.d { "updateMetadata()" }
        playerPtr?.let {
            metadata.duration = SharedVideoPlayer.INSTANCE.getVideoDuration(it).toLong()
            metadata.width = SharedVideoPlayer.INSTANCE.getFrameWidth(it)
            metadata.height = SharedVideoPlayer.INSTANCE.getFrameHeight(it)
            metadata.frameRate = SharedVideoPlayer.INSTANCE.getVideoFrameRate(it)
            macLogger.d { "Metadata updated: $metadata" }
        } ?: macLogger.d { "updateMetadata() - playerPtr is null" }
    }

    /**
     * Starts periodic frame updates.
     */
    private fun startFrameUpdates() {
        macLogger.d { "startFrameUpdates() - Starting frame updates" }
        stopFrameUpdates()
        frameUpdateJob = playerScope.launch {
            while (isActive) {
                updateFrame()
                if (!userDragging) {
                    updatePosition()
                }
                delay(updateInterval)
            }
        }
    }

    /**
     * Stops periodic frame updates.
     */
    private fun stopFrameUpdates() {
        macLogger.d { "stopFrameUpdates() - Stopping frame updates" }
        frameUpdateJob?.cancel()
        frameUpdateJob = null
    }

    /**
     * Updates the current video frame.
     */
    private fun updateFrame() {
        macLogger.d { "updateFrame() - Updating frame" }
        playerPtr?.let { ptr ->
            val width = SharedVideoPlayer.INSTANCE.getFrameWidth(ptr)
            val height = SharedVideoPlayer.INSTANCE.getFrameHeight(ptr)
            macLogger.d { "updateFrame() - Dimensions: width=$width, height=$height" }
            if (width > 0 && height > 0) {
                if (bufferedImage?.width != width || bufferedImage?.height != height) {
                    macLogger.d { "updateFrame() - Creating new BufferedImage" }
                    bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                }
                SharedVideoPlayer.INSTANCE.getLatestFrame(ptr)?.let { framePtr ->
                    val pixels = (bufferedImage!!.raster.dataBuffer as DataBufferInt).data
                    framePtr.getByteBuffer(0, (width * height * 4).toLong())
                        .asIntBuffer().get(pixels)
                    _currentFrameState.value = bufferedImage!!.toComposeImageBitmap()
                    macLogger.d { "updateFrame() - Frame updated" }
                } ?: macLogger.d { "updateFrame() - No frame available" }
            } else {
                macLogger.d { "updateFrame() - Invalid dimensions" }
            }
        } ?: macLogger.d { "updateFrame() - playerPtr is null" }
    }

    /**
     * Updates the playback position and slider.
     */
    private fun updatePosition() {
        macLogger.d { "updatePosition() - Updating position" }
        if (hasMedia && !userDragging) {
            val duration = getDuration()
            if (duration > 0) {
                val current = getCurrentTime()
                macLogger.d { "updatePosition() - current=$current, duration=$duration" }
                if (seekInProgress && targetSeekTime != null) {
                    if (abs(current - targetSeekTime!!) < 0.3) {
                        seekInProgress = false
                        targetSeekTime = null
                    }
                } else {
                    // Conversion to a 0..1000 scale for the Compose slider
                    sliderPos = (current / duration * 1000).toFloat().coerceIn(0f, 1000f)
                    macLogger.d { "updatePosition() - sliderPos updated to $sliderPos" }
                    checkLooping(current, duration)
                }
            } else {
                macLogger.d { "updatePosition() - Invalid duration" }
            }
        } else {
            macLogger.d { "updatePosition() - No media loaded or user is dragging" }
        }
    }

    /**
     * Checks if looping is enabled and restarts the video if needed.
     */
    private fun checkLooping(current: Double, duration: Double) {
        if (loop && current >= duration - 0.5) {
            macLogger.d { "checkLooping() - Loop enabled, restarting video" }
            playerScope.launch { seekTo(0f) }
        }
    }

    override fun play() {
        macLogger.d { "play() - Starting playback" }
        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.playVideo(it)
            isPlaying = true
            macLogger.d { "play() - Playback started, initiating frame updates" }
            startFrameUpdates()
        } ?: macLogger.d { "play() - playerPtr is null" }
    }

    override fun pause() {
        macLogger.d { "pause() - Pausing playback" }
        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.pauseVideo(it)
            isPlaying = false
            macLogger.d { "pause() - Playback paused, updating frame immediately" }
            playerScope.launch { updateFrame() }
            stopFrameUpdates() // Stop frame update job
        } ?: macLogger.d { "pause() - playerPtr is null" }
    }

    override fun stop() {
        macLogger.d { "stop() - Stopping playback" }
        pause()
        seekTo(0f)
    }

    override fun seekTo(value: Float) {
        macLogger.d { "seekTo() - Seeking with slider value: $value" }
        playerScope.launch {
            val duration = getDuration()
            if (duration <= 0) {
                macLogger.d { "seekTo() - Invalid duration, aborting seek" }
                return@launch
            }
            val seekTime = ((value / 1000f) * duration.toFloat()).coerceIn(0f, duration.toFloat())
            macLogger.d { "seekTo() - Calculated seekTime=$seekTime seconds" }
            seekInProgress = true
            targetSeekTime = seekTime.toDouble()
            sliderPos = value

            playerPtr?.let {
                macLogger.d { "seekTo() - Calling seekTo on native player" }
                withContext(Dispatchers.IO) {
                    SharedVideoPlayer.INSTANCE.seekTo(it, seekTime.toDouble())
                }
            } ?: macLogger.d { "seekTo() - playerPtr is null" }

            if (isPlaying) {
                macLogger.d { "seekTo() - Resuming playback after seek" }
                play()
                // Force update of the video frame after a short delay
                delay(100)
                updateFrame()

                // If after 500 ms the seek is still not completed, force its end
                launch {
                    delay(500)
                    if (seekInProgress) {
                        macLogger.d { "seekTo() - Forcing end of seek after timeout" }
                        seekInProgress = false
                        targetSeekTime = null
                    }
                }
            }
        }
    }

    override fun dispose() {
        macLogger.d { "dispose() - Releasing resources" }
        stopFrameUpdates()
        playerScope.cancel()
        playerPtr?.let {
            macLogger.d { "dispose() - Disposing native player" }
            SharedVideoPlayer.INSTANCE.disposeVideoPlayer(it)
            playerPtr = null
        }
        resetState()
    }

    /**
     * Resets the player's state.
     */
    private fun resetState() {
        macLogger.d { "resetState() - Resetting state" }
        hasMedia = false
        isPlaying = false
        _currentFrameState.value = null
        bufferedImage = null
    }

    /**
     * Handles errors by updating the state and logging the error.
     */
    private fun handleError(e: Exception) {
        isLoading = false
        error = VideoPlayerError.SourceError("Error: ${e.message}")
        macLogger.e { "handleError() - Player error: ${e.message}" }
    }

    /**
     * Retrieves the current playback time from the native player.
     */
    private fun getCurrentTime(): Double = playerPtr?.let {
        val time = SharedVideoPlayer.INSTANCE.getCurrentTime(it)
        macLogger.d { "getCurrentTime() - Current time: $time" }
        time
    } ?: run {
        macLogger.d { "getCurrentTime() - playerPtr is null" }
        0.0
    }

    /**
     * Retrieves the total duration of the video from the native player.
     */
    private fun getDuration(): Double = playerPtr?.let {
        val duration = SharedVideoPlayer.INSTANCE.getVideoDuration(it)
        macLogger.d { "getDuration() - Video duration: $duration" }
        duration
    } ?: run {
        macLogger.d { "getDuration() - playerPtr is null" }
        0.0
    }

    // Subtitle methods (stub implementations)
    override fun selectSubtitleTrack(track: SubtitleTrack?) {
        macLogger.d { "selectSubtitleTrack() - Selecting track: $track" }
        currentSubtitleTrack = track
        subtitlesEnabled = track != null
    }

    override fun disableSubtitles() {
        macLogger.d { "disableSubtitles() - Disabling subtitles" }
        subtitlesEnabled = false
        currentSubtitleTrack = null
    }

    override fun clearError() {
        macLogger.d { "clearError()" }
        error = null
    }

    override fun hideMedia() {
        macLogger.d { "hideMedia() - Hiding media" }
        stopFrameUpdates()
        _currentFrameState.value = null
    }

    override fun showMedia() {
        macLogger.d { "showMedia() - Showing media" }
        if (hasMedia) {
            updateFrame()
            if (isPlaying) startFrameUpdates()
        }
    }
}
