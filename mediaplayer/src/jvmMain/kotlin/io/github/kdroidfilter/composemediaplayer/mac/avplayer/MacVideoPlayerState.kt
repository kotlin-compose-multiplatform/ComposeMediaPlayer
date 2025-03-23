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

/**
 * MacVideoPlayerState handles the native Mac video player state.
 *
 * This implementation uses a native video player via SharedVideoPlayer.
 * All debug logs are printed to the console.
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
            println("Volume changed to $_volume")
            playerPtr?.let {
                println("Calling setVolume on native player")
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
            println("Calculating aspectRatio: width=$width, height=$height")
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

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        println("Initializing video player")
        initPlayer()
    }

    /**
     * Initializes the native video player.
     */
    private fun initPlayer() {
        println("initPlayer() - Creating native player")
        playerPtr = SharedVideoPlayer.INSTANCE.createVideoPlayer().also { ptr ->
            if (ptr != null) {
                println("Native player created successfully")
                SharedVideoPlayer.INSTANCE.setVolume(ptr, _volume)
            } else {
                println("Error: Failed to create native player")
                error = VideoPlayerError.UnknownError("Failed to create native player")
            }
        }
    }

    /**
     * Updates the frame rate information from the native player.
     */
    private fun updateFrameRateInfo() {
        println("updateFrameRateInfo()")
        playerPtr?.let {
            videoFrameRate = SharedVideoPlayer.INSTANCE.getVideoFrameRate(it)
            screenRefreshRate = SharedVideoPlayer.INSTANCE.getScreenRefreshRate(it)
            captureFrameRate = SharedVideoPlayer.INSTANCE.getCaptureFrameRate(it)
            println("Frame Rates - Video: $videoFrameRate, Screen: $screenRefreshRate, Capture: $captureFrameRate")
        } ?: println("updateFrameRateInfo() - playerPtr is null")
    }

    override fun openUri(uri: String) {
        println("openUri() - Opening URI: $uri")
        playerScope.launch {
            try {
                isLoading = true
                println("openUri() - isLoading set to true")
                stopFrameUpdates()
                initPlayer()

                withContext(Dispatchers.IO) {
                    println("openUri() - Calling openUri on native player")
                    SharedVideoPlayer.INSTANCE.openUri(playerPtr, uri)
                }

                delay(1000)
                updateFrameRateInfo()
                updateMetadata()

                hasMedia = true
                isLoading = false
                isPlaying = true
                println("openUri() - Media loaded successfully")
                startFrameUpdates()
                updateFrame()

                // Restart playback if already playing
                if (isPlaying) {
                    println("openUri() - Player is in play mode, calling play()")
                    play()
                }
            } catch (e: Exception) {
                println("openUri() - Exception: ${e.message}")
                handleError(e)
            }
        }
    }

    /**
     * Updates the metadata from the native player.
     */
    private fun updateMetadata() {
        println("updateMetadata()")
        playerPtr?.let {
            metadata.duration = SharedVideoPlayer.INSTANCE.getVideoDuration(it).toLong()
            metadata.width = SharedVideoPlayer.INSTANCE.getFrameWidth(it)
            metadata.height = SharedVideoPlayer.INSTANCE.getFrameHeight(it)
            metadata.frameRate = SharedVideoPlayer.INSTANCE.getVideoFrameRate(it)
            println("Metadata updated: $metadata")
        } ?: println("updateMetadata() - playerPtr is null")
    }

    /**
     * Starts periodic frame updates.
     */
    private fun startFrameUpdates() {
        println("startFrameUpdates() - Starting frame updates")
        stopFrameUpdates()
        frameUpdateJob = playerScope.launch {
            while (isActive) {
                if (!userDragging && !seekInProgress) {
                    updateFrame()
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
        println("stopFrameUpdates() - Stopping frame updates")
        frameUpdateJob?.cancel()
        frameUpdateJob = null
    }

    /**
     * Updates the current video frame.
     */
    private fun updateFrame() {
        println("updateFrame() - Updating frame")
        playerPtr?.let { ptr ->
            val width = SharedVideoPlayer.INSTANCE.getFrameWidth(ptr)
            val height = SharedVideoPlayer.INSTANCE.getFrameHeight(ptr)
            println("updateFrame() - Dimensions: width=$width, height=$height")
            if (width > 0 && height > 0) {
                if (bufferedImage?.width != width || bufferedImage?.height != height) {
                    println("updateFrame() - Creating new BufferedImage")
                    bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                }
                SharedVideoPlayer.INSTANCE.getLatestFrame(ptr)?.let { framePtr ->
                    val pixels = (bufferedImage!!.raster.dataBuffer as DataBufferInt).data
                    framePtr.getByteBuffer(0, (width * height * 4).toLong())
                        .asIntBuffer().get(pixels)
                    _currentFrameState.value = bufferedImage!!.toComposeImageBitmap()
                    println("updateFrame() - Frame updated")
                } ?: println("updateFrame() - No frame available")
            } else {
                println("updateFrame() - Invalid dimensions")
            }
        } ?: println("updateFrame() - playerPtr is null")
    }

    /**
     * Updates the playback position and slider.
     */
    private fun updatePosition() {
        println("updatePosition() - Updating position")
        if (hasMedia && !userDragging) {
            val duration = getDuration()
            if (duration > 0) {
                val current = getCurrentTime()
                println("updatePosition() - current=$current, duration=$duration")
                if (seekInProgress && targetSeekTime != null) {
                    if (abs(current - targetSeekTime!!) < 0.3) {
                        seekInProgress = false
                        targetSeekTime = null
                    }
                } else {
                    // Conversion en échelle 0..1000 pour correspondre au slider Compose
                    sliderPos = (current / duration * 1000).toFloat().coerceIn(0f, 1000f)
                    println("updatePosition() - sliderPos updated to $sliderPos")
                    checkLooping(current, duration)
                }
            } else {
                println("updatePosition() - Invalid duration")
            }
        } else {
            println("updatePosition() - No media loaded or user is dragging")
        }
    }

    /**
     * Checks if looping is enabled and restarts the video if needed.
     */
    private fun checkLooping(current: Double, duration: Double) {
        if (loop && current >= duration - 0.5) {
            println("checkLooping() - Loop enabled, restarting video")
            playerScope.launch { seekTo(0f) }
        }
    }

    override fun play() {
        println("play() - Starting playback")
        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.playVideo(it)
            isPlaying = true
            println("play() - Playback started, initiating frame updates")
            startFrameUpdates()
        } ?: println("play() - playerPtr is null")
    }

    override fun pause() {
        println("pause() - Pausing playback")
        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.pauseVideo(it)
            isPlaying = false
            println("pause() - Playback paused, updating frame immediately")
            playerScope.launch { updateFrame() }
            stopFrameUpdates() // Stop frame update job
        } ?: println("pause() - playerPtr is null")
    }

    override fun stop() {
        println("stop() - Stopping playback")
        pause()
        seekTo(0f)
    }

    override fun seekTo(value: Float) {
        println("seekTo() - Seeking with slider value: $value")
        playerScope.launch {
            val duration = getDuration()
            if (duration <= 0) {
                println("seekTo() - Invalid duration, aborting seek")
                return@launch
            }
            // Convertir la valeur du slider (0..1000) en temps en secondes
            val seekTime = ((value / 1000f) * duration.toFloat()).coerceIn(0f, duration.toFloat())
            println("seekTo() - Calculated seekTime=$seekTime seconds")
            seekInProgress = true
            targetSeekTime = seekTime.toDouble()
            // On conserve la valeur du slider en échelle 0..1000
            sliderPos = value

            playerPtr?.let {
                println("seekTo() - Calling seekTo on native player")
                withContext(Dispatchers.IO) {
                    SharedVideoPlayer.INSTANCE.seekTo(it, seekTime.toDouble())
                }
            } ?: println("seekTo() - playerPtr is null")

            if (isPlaying) {
                println("seekTo() - Resuming playback after seek")
                play()
            }
        }
    }

    override fun dispose() {
        println("dispose() - Releasing resources")
        stopFrameUpdates()
        playerScope.cancel()
        playerPtr?.let {
            println("dispose() - Disposing native player")
            SharedVideoPlayer.INSTANCE.disposeVideoPlayer(it)
            playerPtr = null
        }
        resetState()
    }

    /**
     * Resets the player's state.
     */
    private fun resetState() {
        println("resetState() - Resetting state")
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
        println("handleError() - Player error: ${e.message}")
    }

    /**
     * Retrieves the current playback time from the native player.
     */
    private fun getCurrentTime(): Double = playerPtr?.let {
        val time = SharedVideoPlayer.INSTANCE.getCurrentTime(it)
        println("getCurrentTime() - Current time: $time")
        time
    } ?: run {
        println("getCurrentTime() - playerPtr is null")
        0.0
    }

    /**
     * Retrieves the total duration of the video from the native player.
     */
    private fun getDuration(): Double = playerPtr?.let {
        val duration = SharedVideoPlayer.INSTANCE.getVideoDuration(it)
        println("getDuration() - Video duration: $duration")
        duration
    } ?: run {
        println("getDuration() - playerPtr is null")
        0.0
    }

    // Subtitle methods (stub implementations)
    override fun selectSubtitleTrack(track: SubtitleTrack?) {
        println("selectSubtitleTrack() - Selecting track: $track")
        currentSubtitleTrack = track
        subtitlesEnabled = track != null
    }

    override fun disableSubtitles() {
        println("disableSubtitles() - Disabling subtitles")
        subtitlesEnabled = false
        currentSubtitleTrack = null
    }

    override fun clearError() {
        println("clearError()")
        error = null
    }

    override fun hideMedia() {
        println("hideMedia() - Hiding media")
        stopFrameUpdates()
        _currentFrameState.value = null
    }

    override fun showMedia() {
        println("showMedia() - Showing media")
        if (hasMedia) {
            updateFrame()
            if (isPlaying) startFrameUpdates()
        }
    }
}
