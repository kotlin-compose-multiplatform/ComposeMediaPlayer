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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

/**
 * MacOS implementation of the PlatformVideoPlayerState interface.
 * Uses AVPlayer via JNA to play media and shared buffer for video rendering.
 * Enhanced based on working Swing implementation.
 */
class MacVideoPlayerState : PlatformVideoPlayerState {
    // Native player reference
    private var playerPtr: Pointer? = null

    // Frame management
    private var _currentFrameState = mutableStateOf<ImageBitmap?>(null)
    internal val currentFrameState: State<ImageBitmap?> = _currentFrameState
    private var frameUpdateJob: Job? = null
    private var bufferedImage: BufferedImage? = null

    // Player state
    override var hasMedia: Boolean by mutableStateOf(false)
    override var isPlaying: Boolean by mutableStateOf(false)

    // Volume management with backing property
    private var _volume by mutableStateOf(1.0f)
    override var volume: Float
        get() = _volume
        set(value) {
            // Constrain the volume to the range 0.0-1.0
            _volume = value.coerceIn(0f..1f)
            // Apply the volume to the native player
            playerPtr?.let {
                SharedVideoPlayer.INSTANCE.setVolume(it, _volume)
            }
        }

    override var sliderPos: Float by mutableStateOf(0.0f)
    override var userDragging: Boolean by mutableStateOf(false)
    override var loop: Boolean by mutableStateOf(false)
    override val leftLevel: Float by mutableStateOf(0.0f)
    override val rightLevel: Float by mutableStateOf(0.0f)
    override var isLoading: Boolean by mutableStateOf(false)
    override var error: VideoPlayerError? by mutableStateOf(null)

    // Text representations
    override val positionText: String
        get() = formatTime(getCurrentTime())
    override val durationText: String
        get() = formatTime(getDuration())

    // Aspect ratio calculation
    internal val aspectRatio: Float
        get() {
            val width = if (playerPtr != null) SharedVideoPlayer.INSTANCE.getFrameWidth(playerPtr) else 16
            val height = if (playerPtr != null) SharedVideoPlayer.INSTANCE.getFrameHeight(playerPtr) else 9
            return if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 16f / 9f
        }

    // Video metadata
    override val metadata: VideoMetadata = VideoMetadata()

    // Subtitle support
    override var subtitlesEnabled: Boolean by mutableStateOf(false)
    override var currentSubtitleTrack: SubtitleTrack? by mutableStateOf(null)
    override val availableSubtitleTracks: MutableList<SubtitleTrack> = mutableListOf()

    // Frame rate information (derived from Swing implementation)
    private var videoFrameRate: Float = 0.0f
    private var screenRefreshRate: Float = 0.0f
    private var captureFrameRate: Float = 0.0f

    // Calculated update interval based on frame rates
    private val updateInterval: Long
        get() = if (captureFrameRate > 0) (1000.0f / captureFrameRate).toLong() else 33L

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        initPlayer()
    }

    private fun initPlayer() {
        playerPtr = SharedVideoPlayer.INSTANCE.createVideoPlayer()
        if (playerPtr == null) {
            println("Failed to create the native video player for MacOS")
            error = VideoPlayerError.UnknownError("Failed to create native player")
        } else {
            // Initialize the volume on player creation
            SharedVideoPlayer.INSTANCE.setVolume(playerPtr, _volume)
        }
    }

    /**
     * Updates frame rate information from the native player
     * (Adapted from Swing implementation)
     */
    private fun updateFrameRateInfo() {
        playerPtr?.let { ptr ->
            videoFrameRate = SharedVideoPlayer.INSTANCE.getVideoFrameRate(ptr)
            screenRefreshRate = SharedVideoPlayer.INSTANCE.getScreenRefreshRate(ptr)
            captureFrameRate = SharedVideoPlayer.INSTANCE.getCaptureFrameRate(ptr)
            println("Frame rates - Video: $videoFrameRate fps, Screen: $screenRefreshRate Hz, Capture: $captureFrameRate fps")
        }
    }

    override fun openUri(uri: String) {
        try {
            isLoading = true
            error = null

            if (playerPtr == null) {
                initPlayer()
            }

            // Stop any existing playback and frame updates
            stopFrameUpdates()

            // Open the media URI
            SharedVideoPlayer.INSTANCE.openUri(playerPtr, uri)

            // Start a delay to allow media to initialize
            playerScope.launch {
                delay(1000) // Increased delay to ensure media is fully prepared

                // Update frame rate info
                updateFrameRateInfo()

                // Update metadata once media is loaded
                updateMetadata()

                val duration = getDuration()
                if (duration <= 0) {
                    delay(500) // Additional delay for problematic media
                }

                hasMedia = true
                isLoading = false

                // Start frame updates
                startFrameUpdates()

                // Set volume for new media
                playerPtr?.let {
                    SharedVideoPlayer.INSTANCE.setVolume(it, _volume)
                }

                // Get initial frame to display even if paused
                updateFrame()

                // Auto-play if previously playing
                if (isPlaying) {
                    play()
                }
            }
        } catch (e: Exception) {
            isLoading = false
            error = VideoPlayerError.SourceError("Error opening media: ${e.message}")
            println("Error opening media: ${e.message}")
        }
    }

    private fun updateMetadata() {
        val duration = getDuration()


        // Get frame dimensions
        if (playerPtr != null) {
            val width = SharedVideoPlayer.INSTANCE.getFrameWidth(playerPtr)
            val height = SharedVideoPlayer.INSTANCE.getFrameHeight(playerPtr)
            if (width > 0 && height > 0) {
                metadata.width = width
                metadata.height = height
            }
        }

        // Get video frame rate
        if (playerPtr != null) {
            val frameRate = SharedVideoPlayer.INSTANCE.getVideoFrameRate(playerPtr)
            if (frameRate > 0) {
                metadata.frameRate = frameRate
                videoFrameRate = frameRate
            }
        }
    }

    private fun startFrameUpdates() {
        stopFrameUpdates()

        frameUpdateJob = playerScope.launch {
            while (isActive) {
                updateFrame()
                updatePosition()
                delay(updateInterval)
            }
        }
    }

    private fun stopFrameUpdates() {
        frameUpdateJob?.cancel()
        frameUpdateJob = null
    }

    private fun updateFrame() {
        if (playerPtr == null || !hasMedia) return

        val width = SharedVideoPlayer.INSTANCE.getFrameWidth(playerPtr)
        val height = SharedVideoPlayer.INSTANCE.getFrameHeight(playerPtr)
        if (width <= 0 || height <= 0) return

        val framePtr = SharedVideoPlayer.INSTANCE.getLatestFrame(playerPtr) ?: return

        if (bufferedImage == null || bufferedImage!!.width != width || bufferedImage!!.height != height) {
            bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        }

        val pixelArray = (bufferedImage!!.raster.dataBuffer as DataBufferInt).data
        val byteBuffer: ByteBuffer = framePtr.getByteBuffer(0, width.toLong() * height * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intBuffer: IntBuffer = byteBuffer.asIntBuffer()
        intBuffer.get(pixelArray)

        // Convert to ImageBitmap for Compose
        _currentFrameState.value = bufferedImage!!.toComposeImageBitmap()
    }

    private fun updatePosition() {
        if (playerPtr == null || !hasMedia || userDragging) return

        val duration = getDuration()
        if (duration > 0) {
            val current = getCurrentTime()
            val newSliderPos = (current / duration).toFloat().coerceIn(0f, 1f)
            if (newSliderPos != sliderPos) {
                sliderPos = newSliderPos
            }

            // Handle looping
            if (loop && current >= duration - 0.5) {
                seekTo(0f)
            }
        }
    }

    override fun play() {
        if (playerPtr != null && hasMedia) {
            SharedVideoPlayer.INSTANCE.playVideo(playerPtr)
            // Apply volume setting when playing
            SharedVideoPlayer.INSTANCE.setVolume(playerPtr, _volume)
            isPlaying = true
            startFrameUpdates()
        }
    }

    override fun pause() {
        if (playerPtr != null && hasMedia) {
            SharedVideoPlayer.INSTANCE.pauseVideo(playerPtr)
            isPlaying = false
            // Ensure we get one last frame update to show current position
            playerScope.launch {
                delay(100)
                updateFrame()
            }
        }
    }

    override fun stop() {
        if (playerPtr != null && hasMedia) {
            pause()
            seekTo(0f)
        }
    }

    override fun seekTo(value: Float) {
        if (playerPtr != null && hasMedia) {
            val duration = getDuration()
            if (duration <= 0) return

            val seekValue = value.coerceIn(0f, 1f)
            val timeToSeek = seekValue * duration

            // Update the UI immediately for responsive feeling
            sliderPos = seekValue

            // Perform the actual seek
            SharedVideoPlayer.INSTANCE.seekTo(playerPtr, timeToSeek)

            // Update frame after seeking
            playerScope.launch {
                delay(100) // Short delay for seek to complete
                updateFrame()

                // If playing was paused during seek, resume it
                if (isPlaying && !userDragging) {
                    play()
                }
            }
        }
    }

    override fun dispose() {
        stopFrameUpdates()
        playerScope.cancel()

        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.disposeVideoPlayer(it)
            playerPtr = null
        }

        hasMedia = false
        isPlaying = false
        _currentFrameState.value = null
        bufferedImage = null
    }

    override fun clearError() {
        error = null
    }

    override fun hideMedia() {
        stopFrameUpdates()
        _currentFrameState.value = null
    }

    override fun showMedia() {
        if (hasMedia) {
            updateFrame() // Get the current frame immediately
            if (isPlaying) {
                startFrameUpdates()
            }
        }
    }

    /**
     * Gets the current playback time in seconds.
     */
    private fun getCurrentTime(): Double {
        return playerPtr?.let { SharedVideoPlayer.INSTANCE.getCurrentTime(it) } ?: 0.0
    }

    /**
     * Gets the total duration of the media in seconds.
     */
    private fun getDuration(): Double {
        return playerPtr?.let { SharedVideoPlayer.INSTANCE.getVideoDuration(it) } ?: 0.0
    }

    override fun selectSubtitleTrack(track: SubtitleTrack?) {
        // Currently not implemented for MacOS
        currentSubtitleTrack = track
        subtitlesEnabled = track != null
    }

    override fun disableSubtitles() {
        subtitlesEnabled = false
        currentSubtitleTrack = null
    }
}