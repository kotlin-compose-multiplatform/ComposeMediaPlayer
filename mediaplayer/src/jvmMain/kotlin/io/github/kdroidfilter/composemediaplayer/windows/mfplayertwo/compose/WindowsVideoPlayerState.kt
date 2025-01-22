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
import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.util.Logger
import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.wrapper.AudioControl
import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.wrapper.MediaPlayerSlider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.awt.Canvas
import java.io.File
import java.io.FileNotFoundException
import java.time.Duration
import kotlin.time.Duration.Companion.seconds

class WindowsVideoPlayerState : PlatformVideoPlayerState {
    companion object {
        private const val UPDATE_INTERVAL = 60L
        private val LOADING_TIMEOUT = 10.seconds
        private val logger = Logger("WindowsVideoPlayerState")
    }

    private val mediaPlayer = MediaPlayerLib.INSTANCE
    private val audioControl = AudioControl(mediaPlayer)
    private val mediaSlider = MediaPlayerSlider(mediaPlayer)
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // State holders
    private var videoCanvas: Canvas? = null
    private var loadingTimeoutJob: Job? = null
    private var videoUpdateJob: Job? = null

    // Compose state
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
        // Convert internal 0..1 progress to 0..1000 for UI
        get() = _progress * 1000f
        set(value) {
            if (!isInitialized) return

            // Convert UI 0..1000 to internal 0..1
            val fraction = (value / 1000f).coerceIn(0f, 1f)

            coroutineScope.launch {
                userDragging = true
                try {
                    logger.log("Setting slider position: $value => fraction=$fraction")
                    if (mediaSlider.setProgress(fraction)) {
                        _progress = fraction
                        _currentTime = _duration * fraction
                        logger.log("Seek success => new progress=$fraction")
                    } else {
                        logger.error("Seek failed => setProgress returned false")
                    }
                } catch (e: Exception) {
                    logger.error("Error during seek: ${e.message}")
                } finally {
                    userDragging = false
                }
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

    override val positionText: String get() = formatTime(_currentTime)
    override val durationText: String get() = formatTime(_duration)

    override var isLoading by mutableStateOf(false)
        private set

    private var _error: VideoPlayerError? by mutableStateOf(null)
    override val error: VideoPlayerError? get() = _error

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Video update flow
    private fun startVideoUpdates() {
        videoUpdateJob?.cancel()
        videoUpdateJob = coroutineScope.launch {
            flow {
                while (currentCoroutineContext().isActive) {
                    emit(Unit)
                    delay(UPDATE_INTERVAL)
                }
            }
                .flowOn(Dispatchers.Default)
                .filter { isInitialized && !userDragging }
                .onEach {
                    withContext(Dispatchers.Main) {
                        updateVideo()
                        updateProgress()
                    }
                }
                .catch { e ->
                    logger.error("Error in video updates: ${e.message}")
                }
                .collect()
        }
    }

    private fun updateProgress() {
        if (!userDragging && mediaPlayer.IsInitialized()) {
            try {
                mediaSlider.getDurationInSeconds()?.let { duration ->
                    if (duration > 0.0) {
                        _duration = duration
                        mediaSlider.getCurrentPositionInSeconds()?.let { position ->
                            _currentTime = position
                            // Store progress as 0..1 internally
                            _progress = (_currentTime / _duration).toFloat().coerceIn(0f, 1f)
                            logger.log("Progress updated: $_progress (time: $_currentTime / $_duration)")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error updating progress: ${e.message}")
            }
        }
    }

    fun initializeWithCanvas(canvas: Canvas) = coroutineScope.launch {
        if (isInitialized) {
            logger.log("Player is already initialized")
            return@launch
        }

        try {
            val hwnd = Native.getComponentPointer(canvas) ?: run {
                logger.error("initializeWithCanvas: NULL HWND, Canvas not displayable")
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (!canvas.isDisplayable) {
                    canvas.createBufferStrategy(1)
                }

                val callback = MediaPlayerLib.MediaPlayerCallback(::onMediaEvent)

                mediaPlayer.InitializeMediaPlayer(WinDef.HWND(hwnd), callback).let { hr ->
                    if (hr < 0) {
                        handleError("Initialization failed (HR=0x${hr.toString(16)})")
                        return@withContext
                    }

                    videoCanvas = canvas
                    isInitialized = true
                    startVideoUpdates()
                    logger.log("Player successfully initialized")
                }
            }
        } catch (e: Exception) {
            handleError("Initialization error: ${e.message}")
        }
    }

    override fun openUri(uri: String) {
        if (!isInitialized) {
            handleError("Media player not initialized")
        }

        resetState()

        try {
            val hr =
                when {
                    uri.startsWith("http", ignoreCase = true) -> {
                        logger.log("Opening URL: $uri")
                        mediaPlayer.PlayURL(WString(uri))
                    }
                    else -> {
                        val file = File(uri).absoluteFile
                        if (!file.exists()) throw FileNotFoundException("File does not exist: $uri")
                        logger.log("Opening file: ${file.absolutePath}")
                        mediaPlayer.PlayFile(WString(file.absolutePath))
                    }
                }


            if (hr < 0) {
                handleError("Unable to open media (HR=0x${hr.toString(16)})")
                return
            }

            startLoadingTimeout()
        } catch (e: Exception) {
            handleError("Error opening media: ${e.message}")
        }
    }

    private fun startLoadingTimeout() {
        loadingTimeoutJob?.cancel()
        loadingTimeoutJob = coroutineScope.launch {
            delay(LOADING_TIMEOUT)
            if (isLoading) {
                handleError("Timeout while loading media")
            }
        }
    }

    private fun resetState() {
        loadingTimeoutJob?.cancel()
        _isPlaying = false
        clearError()
        isLoading = false
        _currentTime = 0.0
        _duration = 0.0
        _progress = 0f
    }

    override fun play() = mediaOperation("start playback") { mediaPlayer.ResumePlayback() }
    override fun pause() = mediaOperation("pause playback") { mediaPlayer.PausePlayback() }
    override fun stop() = mediaOperation("stop playback") { mediaPlayer.StopPlayback() }

    private fun mediaOperation(operation: String, block: () -> Int) {
        if (!isInitialized) return

        val hr = block()
        if (hr < 0) {
            handleError("Unable to $operation (HR=0x${hr.toString(16)})")
        }
    }

    override fun seekTo(value: Float) {
        if (!isInitialized) return

        coroutineScope.launch {
            try {
                val fraction = (value / 1000f).coerceIn(0f, 1f)
                if (mediaSlider.setProgress(fraction)) {
                    _progress = fraction
                    _currentTime = _duration * fraction
                    logger.log("SeekTo($value) => fraction=$fraction => OK")
                } else {
                    logger.error("SeekTo($value) => fraction=$fraction => FAILED")
                }
            } catch (e: Exception) {
                logger.error("Error during seek: ${e.message}")
            }
        }
    }

    override fun dispose() {
        if (!isInitialized) return

                 loadingTimeoutJob?.cancel()

            try {
                mediaPlayer.StopPlayback()
                mediaPlayer.CleanupMediaPlayer()
            } catch (e: Exception) {
                logger.error("Error during cleanup: ${e.message}")
            } finally {
                cleanupState()
            }

    }

    private fun cleanupState() {
        isInitialized = false
        _isPlaying = false
        _currentTime = 0.0
        _duration = 0.0
        _progress = 0f
        videoCanvas = null
        logger.log("Player cleaned up")
    }

    override fun clearError() {
        errorMessage = null
        _error = null
    }

    fun updateVideo() {
        if (isInitialized) {
            mediaPlayer.UpdateVideo()
        }
    }

    private fun onMediaEvent(eventType: Int, hr: Int) {
        logger.log("Event: $eventType (HR=0x${hr.toString(16)})")

        when (eventType) {
            MediaPlayerLib.MP_EVENT_MEDIAITEM_SET -> updateProgress()
            MediaPlayerLib.MP_EVENT_PLAYBACK_STARTED -> handlePlaybackStarted()
            MediaPlayerLib.MP_EVENT_PLAYBACK_PAUSED -> { _isPlaying = false }
            MediaPlayerLib.MP_EVENT_PLAYBACK_STOPPED -> handlePlaybackStopped()
            MediaPlayerLib.MP_EVENT_PLAYBACK_ERROR -> handlePlaybackError(hr)
            MediaPlayerLib.MP_EVENT_LOADING_STARTED -> handleLoadingStarted()
            MediaPlayerLib.MP_EVENT_LOADING_COMPLETE -> handleLoadingComplete()
        }
    }

    private fun handlePlaybackStarted() {
        _isPlaying = true
        isLoading = false
        loadingTimeoutJob?.cancel()
    }

    private fun handlePlaybackStopped() {
        _isPlaying = false
        isLoading = false
        _currentTime = 0.0
        _progress = 0f

        if (loop) {
            mediaSlider.setProgress(0f)
            play()
        }
    }

    private fun handlePlaybackError(hr: Int) {
        _isPlaying = false
        isLoading = false
        loadingTimeoutJob?.cancel()
        handleError("Playback error (HR=0x${hr.toString(16)})")
    }

    private fun handleLoadingStarted() {
        isLoading = true
        logger.log("Loading started")
    }

    private fun handleLoadingComplete() {
        loadingTimeoutJob?.cancel()
        isLoading = false
        logger.log("Loading complete")
    }

    private fun handleError(message: String) {
        errorMessage = message
        _error = VideoPlayerError.UnknownError(message)
        logger.error(message)
    }

    private fun formatTime(value: Double): String {
        val duration = Duration.ofSeconds(value.toLong())
        return duration.toHours().let { hours ->
            when {
                hours > 0 -> "%02d:%02d:%02d".format(hours, duration.toMinutesPart(), duration.toSecondsPart())
                else -> "%02d:%02d".format(duration.toMinutesPart(), duration.toSecondsPart())
            }
        }
    }


}