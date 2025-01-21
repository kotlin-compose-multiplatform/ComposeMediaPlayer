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
    private var videoUpdateJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    var isInitialized by mutableStateOf(false)
        private set

    private var _isPlaying by mutableStateOf(false)
    override val isPlaying: Boolean
        get() = _isPlaying

    private var _volume by mutableStateOf(1f)
    override var volume: Float
        get() = _volume
        set(value) {
            _volume = value.coerceIn(0f, 1f)
            audioControl.setVolume(_volume)
        }

    private var _currentTime by mutableStateOf(0.0)
    private var _duration by mutableStateOf(0.0)
    private var _progress by mutableStateOf(0f)

    override var sliderPos: Float
        get() = _progress
        set(value) {
            if (isInitialized && value in 0f..1f) {
                userDragging = true
                log("Setting slider position: $value")
                val ok = mediaSlider.setProgress(value)
                if (ok) {
                    _progress = value
                    log("Seek success => new progress=$value")
                } else {
                    log("Seek failed => setProgress returned false")
                }
                userDragging = false
            }
        }

    private var _userDragging by mutableStateOf(false)
    override var userDragging: Boolean
        get() = _userDragging
        set(value) { _userDragging = value }

    private var _loop by mutableStateOf(false)
    override var loop: Boolean
        get() = _loop
        set(value) { _loop = value }

    override val leftLevel: Float get() = 0f
    override val rightLevel: Float get() = 0f

    override val positionText: String get() = formatSeconds(_currentTime)
    override val durationText: String get() = formatSeconds(_duration)

    override var isLoading by mutableStateOf(false)
    private var _error: VideoPlayerError? by mutableStateOf(null)
    override val error: VideoPlayerError? get() = _error
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // --------------------------------
    //  Start/Stop Updates
    // --------------------------------
    private fun startVideoUpdates() {
        videoUpdateJob?.cancel()
        videoUpdateJob = coroutineScope.launch {
            while (isActive) {
                if (isInitialized && !userDragging) {
                    withContext(Dispatchers.Main) {
                        updateVideo()
                        updateProgress()
                    }
                }
                delay(60)
            }
        }
    }

    private fun stopVideoUpdates() {
        videoUpdateJob?.cancel()
        videoUpdateJob = null
    }

    private fun updateProgress() {
        try {
            if (!userDragging && mediaPlayer.IsInitialized()) {
                // Retrieve the duration first
                val duration = mediaSlider.getDurationInSeconds() ?: return
                if (duration <= 0.0) return
                _duration = duration

                // Retrieve the current position
                val position = mediaSlider.getCurrentPositionInSeconds() ?: return
                _currentTime = position

                // Manually calculate progress instead of relying on getProgress
                _progress = (_currentTime / _duration).toFloat()

                log("Progress updated: time=${formatSeconds(_currentTime)}/${formatSeconds(_duration)}, progress=$_progress")
            }
        } catch (e: Exception) {
            log("Error updating progress: ${e.message}")
        }
    }

    // --------------------------------
    //  Initialization Methods
    // --------------------------------
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
                startVideoUpdates()
                log("Player successfully initialized")
            }
        } catch (e: Exception) {
            log("Error during initialization: ${e.message}")
            e.printStackTrace()
        }
    }

    // --------------------------------
    //  Source Management
    // --------------------------------
    override fun openUri(uri: String) {
        if (!isInitialized) {
            val msg = "Media player not initialized"
            errorMessage = msg
            _error = VideoPlayerError.SourceError(msg)
            log(msg)
            return
        }

        loadingTimeoutJob?.cancel()
        _isPlaying = false
        errorMessage = null
        _error = null
        isLoading = false
        _currentTime = 0.0
        _duration = 0.0
        _progress = 0f

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

                // Timeout if loading exceeds 10s
                loadingTimeoutJob = launch {
                    delay(10000)
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

    // --------------------------------
    //  Playback Controls
    // --------------------------------
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
        userDragging = true
        log("Seeking to: $value")

        val ok = mediaSlider.setProgress(value)
        if (ok) {
            _progress = value
            log("SeekTo($value) => OK")
        } else {
            log("SeekTo($value) => FAILED")
        }

        userDragging = false
    }

    // --------------------------------
    //  Cleanup (to be called at the right time)
    // --------------------------------
    override fun dispose() {
        if (isInitialized) {
            stopVideoUpdates()
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
            _currentTime = 0.0
            _duration = 0.0
            _progress = 0f
            videoCanvas = null
            log("dispose: MediaPlayer cleaned up")
        }
    }

    override fun clearError() {
        errorMessage = null
        _error = null
    }

    // --------------------------------
    //  Updates
    // --------------------------------
    fun updateVideo() {
        if (isInitialized) {
            mediaPlayer.UpdateVideo()
        }
    }

    private fun onMediaEvent(eventType: Int, hr: Int) {
        log("onMediaEvent: $eventType (HR=0x${hr.toString(16)})")
        when (eventType) {
            MediaPlayerLib.MP_EVENT_MEDIAITEM_CREATED -> {
                // Waiting for MP_EVENT_MEDIAITEM_SET
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
                _currentTime = 0.0
                _progress = 0f

                if (loop) {
                    mediaSlider.setProgress(0f)
                    play()
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
