package io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef
import io.github.kdroidfilter.composemediaplayer.PlatformVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.MediaPlayerLib
import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.wrapper.AudioControl
import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.wrapper.MediaPlayerSlider
import kotlinx.coroutines.*
import java.awt.Canvas
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.SwingUtilities


class WindowsVideoPlayerState : PlatformVideoPlayerState {
    private val mediaPlayer = MediaPlayerLib.INSTANCE
    private val audioControl = AudioControl(mediaPlayer)
    private val mediaSlider = MediaPlayerSlider(mediaPlayer)
    private var videoCanvas: Canvas? = null
    private var loadingTimeoutJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Initialization state
    var isInitialized by mutableStateOf(false)
        private set

    // Playback states
    private var _isPlaying by mutableStateOf(false)
    override val isPlaying: Boolean
        get() = _isPlaying

    // Volume
    private var _volume by mutableStateOf(1f)
    override var volume: Float
        get() = _volume
        set(value) {
            _volume = value.coerceIn(0f, 1f)
            audioControl.setVolume(_volume)
        }

    // Slider position
    override var sliderPos: Float
        get() {
            val duration = mediaSlider.getDurationInSeconds() ?: 0.0
            val position = mediaSlider.getCurrentPositionInSeconds() ?: 0.0
            return if (duration > 0.0) (position / duration).toFloat() else 0f
        }
        set(value) {
            if (isInitialized && value in 0f..1f) {
                val duration = mediaSlider.getDurationInSeconds() ?: return
                mediaSlider.setPositionInSeconds(duration * value)
            }
        }

    // User control states
    private var _userDragging by mutableStateOf(false)
    override var userDragging: Boolean
        get() = _userDragging
        set(value) { _userDragging = value }

    // Loop mode
    private var _loop by mutableStateOf(false)
    override var loop: Boolean
        get() = _loop
        set(value) { _loop = value }

    // Audio levels (not implemented)
    override val leftLevel: Float get() = 0f
    override val rightLevel: Float get() = 0f

    // Time states
    private var _currentTime by mutableStateOf(0.0)
    private var _duration by mutableStateOf(0.0)

    override val positionText: String get() = formatSeconds(_currentTime)
    override val durationText: String get() = formatSeconds(_duration)

    // Loading state and errors
    override var isLoading by mutableStateOf(false)
    private var _error: VideoPlayerError? by mutableStateOf(null)
    override val error: VideoPlayerError? get() = _error
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun checkPlayerState(): Boolean {
        if (!isInitialized) {
            log("Player is not initialized")
            return false
        }

        if (videoCanvas == null) {
            log("No canvas available")
            return false
        }

        return true
    }

    fun initializeWithCanvas(canvas: Canvas) {
        if (isInitialized) {
            log("Player is already initialized")
            return
        }

        try {
            val hwnd = Native.getComponentPointer(canvas)
            if (hwnd == null) {
                log("initializeWithCanvas: NULL HWND, Canvas not displayable")
                return
            }

            log("initializeWithCanvas: HWND=$hwnd")

            val callback = MediaPlayerLib.MediaPlayerCallback { eventType, result ->
                SwingUtilities.invokeLater {
                    onMediaEvent(eventType, result)
                }
            }

            // Wait for canvas to be actually ready
            SwingUtilities.invokeLater {
                if (!canvas.isDisplayable) {
                    canvas.createBufferStrategy(1)
                }

                val hr = mediaPlayer.InitializeMediaPlayer(WinDef.HWND(hwnd), callback)
                if (hr < 0) {
                    errorMessage = "Initialization failed (HR=0x${hr.toString(16)})"
                    _error = VideoPlayerError.UnknownError(errorMessage!!)
                    log(errorMessage!!)
                    return@invokeLater
                }

                videoCanvas = canvas
                isInitialized = true
                log("Player successfully initialized")
            }
        } catch (e: Exception) {
            log("Error during initialization: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun openUri(uri: String) {
        if (!isInitialized) {
            val msg = "Media player not initialized"
            errorMessage = msg
            _error = VideoPlayerError.SourceError(msg)
            log(msg)
            return
        }

        // Cancel ongoing operations
        loadingTimeoutJob?.cancel()
        _isPlaying = false

        // Reset states
        errorMessage = null
        _error = null
        isLoading = false

        coroutineScope.launch {
            try {
                val hr = if (uri.startsWith("http", true)) {
                    log("Opening URL: $uri")
                    mediaPlayer.PlayURL(WString(uri))
                } else {
                    val file = File(uri).absoluteFile
                    if (!file.exists()) {
                        throw Exception("File does not exist: $uri")
                    }
                    log("Opening file: ${file.absolutePath}")

                    // Use withContext to ensure operation is completed
                    withContext(Dispatchers.IO) {
                        mediaPlayer.PlayFile(WString(file.absolutePath))
                    }
                }

                if (hr < 0) {
                    val msg = "Unable to open media (HR=0x${hr.toString(16)})"
                    withContext(Dispatchers.Main) {
                        errorMessage = msg
                        _error = VideoPlayerError.SourceError(msg)
                        isLoading = false
                    }
                    log(msg)
                    return@launch
                }

                // Set up timeout
                loadingTimeoutJob = launch {
                    delay(10000) // 10 seconds timeout
                    if (isLoading) {
                        isLoading = false
                        val msg = "Timeout while loading media"
                        errorMessage = msg
                        _error = VideoPlayerError.UnknownError(msg)
                        log(msg)
                    }
                }

            } catch (e: Exception) {
                val msg = "Error openUri: ${e.message}"
                withContext(Dispatchers.Main) {
                    errorMessage = msg
                    _error = VideoPlayerError.SourceError(msg)
                    isLoading = false
                }
                log(msg)
                e.printStackTrace()
            }
        }
    }

    override fun play() {
        if (!isInitialized) return
        val hr = mediaPlayer.ResumePlayback()
        if (hr < 0) {
            val msg = "Unable to start playback (HR=0x${hr.toString(16)})"
            errorMessage = msg
            _error = VideoPlayerError.UnknownError(msg)
            log(msg)
        }
    }

    override fun pause() {
        if (!isInitialized) return
        val hr = mediaPlayer.PausePlayback()
        if (hr < 0) {
            val msg = "Unable to pause playback (HR=0x${hr.toString(16)})"
            errorMessage = msg
            _error = VideoPlayerError.UnknownError(msg)
            log(msg)
        }
    }

    override fun stop() {
        if (!isInitialized) return
        val hr = mediaPlayer.StopPlayback()
        if (hr < 0) {
            val msg = "Unable to stop playback (HR=0x${hr.toString(16)})"
            errorMessage = msg
            _error = VideoPlayerError.UnknownError(msg)
            log(msg)
        }
    }

    override fun seekTo(value: Float) {
        if (!isInitialized) return
        mediaSlider.setPositionInSeconds(value.toDouble())
    }

    override fun dispose() {
        if (isInitialized) {
            loadingTimeoutJob?.cancel()
            coroutineScope.cancel()

            try {
                mediaPlayer.StopPlayback()
                mediaPlayer.CleanupMediaPlayer()
            } catch (e: Exception) {
                log("Error during cleanup: ${e.message}")
            }

            isInitialized = false
            _isPlaying = false
            videoCanvas = null
            log("dispose: MediaPlayer cleaned up")
        }
    }

    override fun clearError() {
        errorMessage = null
        _error = null
    }

    fun updateVideo() {
        if (isInitialized) {
            mediaPlayer.UpdateVideo()
            updateProgress()
        }
    }

    private fun onMediaEvent(eventType: Int, hr: Int) {
        log("onMediaEvent: $eventType (HR=0x${hr.toString(16)})")
        when (eventType) {
            MediaPlayerLib.MP_EVENT_MEDIAITEM_CREATED -> {
                // Wait for MP_EVENT_MEDIAITEM_SET
            }
            MediaPlayerLib.MP_EVENT_MEDIAITEM_SET -> {
                updateProgress()
            }
            MediaPlayerLib.MP_EVENT_PLAYBACK_STARTED -> {
                _isPlaying = true
                isLoading = false
                loadingTimeoutJob?.cancel()
            }
            MediaPlayerLib.MP_EVENT_PLAYBACK_PAUSED -> {
                _isPlaying = false
            }
            MediaPlayerLib.MP_EVENT_PLAYBACK_STOPPED -> {
                _isPlaying = false
                isLoading = false
                if (loop) {
                    // Optional: Reimplement loop logic here if needed
                    // mediaPlayer.SetPosition(0)
                    // mediaPlayer.ResumePlayback()
                }
            }
            MediaPlayerLib.MP_EVENT_PLAYBACK_ERROR -> {
                _isPlaying = false
                isLoading = false
                loadingTimeoutJob?.cancel()
                val msg = "Playback error (HR=0x${hr.toString(16)})"
                errorMessage = msg
                _error = VideoPlayerError.UnknownError(msg)
            }
            MediaPlayerLib.MP_EVENT_LOADING_STARTED -> {
                isLoading = true
                log("Loading started")
            }
            MediaPlayerLib.MP_EVENT_LOADING_COMPLETE -> {
                loadingTimeoutJob?.cancel()
                isLoading = false
                log("Loading complete")
            }
        }
    }

    private fun updateProgress() {
        mediaSlider.getDurationInSeconds()?.let { dur ->
            _duration = dur
        }
        mediaSlider.getCurrentPositionInSeconds()?.let { pos ->
            _currentTime = pos
        }
    }

    private fun formatSeconds(value: Double): String {
        val duration = Duration.ofSeconds(value.toLong())
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart()
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun log(msg: String) {
        val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
        println("[$ts][WindowsVideoPlayerState] $msg")
    }
}