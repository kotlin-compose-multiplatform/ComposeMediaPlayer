package io.github.kdroidfilter.composemediaplayer.javafx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.composemediaplayer.PlatformVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoMetadata
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import java.io.File
import java.time.Duration.ofSeconds

class JavaFxVideoPlayerState : PlatformVideoPlayerState {
    private var currentMediaView: MediaView? = null
    internal var mediaPlayer: MediaPlayer? = null

    // Basic states
    private var _volume by mutableStateOf(1f)
    private var _sliderPos by mutableStateOf(0f)
    private var _loop by mutableStateOf(false)
    private var _isPlaying by mutableStateOf(false)
    private var _leftLevel by mutableStateOf(0f)
    private var _rightLevel by mutableStateOf(0f)
    private var _currentTime by mutableStateOf(0.0)
    private var _duration by mutableStateOf(0.0)
    private var _isLoading by mutableStateOf(false)
    private var _error by mutableStateOf<VideoPlayerError?>(null)
    override var userDragging by mutableStateOf(false)

    // Public properties with getters and setters
    override var volume: Float
        get() = _volume
        set(value) {
            _volume = value.coerceIn(0f, 1f)
            updateVolume()
        }

    override var sliderPos: Float
        get() = _sliderPos
        set(value) {
            _sliderPos = value
            if (!userDragging) {
                handleSliderChange(value)
            }
        }

    override var loop: Boolean
        get() = _loop
        set(value) {
            _loop = value
            updateLoopStatus()
        }

    // Public getters
    override val isPlaying: Boolean get() = _isPlaying
    override val leftLevel: Float get() = _leftLevel
    override val rightLevel: Float get() = _rightLevel
    override val positionText: String get() = formatTime(_currentTime)
    override val durationText: String get() = formatTime(_duration)
    override val isLoading: Boolean get() = _isLoading
    override val error: VideoPlayerError? get() = _error
    override val metadata: VideoMetadata = VideoMetadata()


    // Main function to update the MediaView
    fun updateMediaView(view: MediaView) {
        runOnFxThread {
            currentMediaView = view
            mediaPlayer?.let { player ->
                view.mediaPlayer = player
            }
        }
    }

    // Core media control functions
    override fun openUri(uri: String) {
        runOnFxThread {
            try {
                _isLoading = true
                _error = null

                // Stop the current media
                stopMedia()

                // Create media URL
                val fileOrUrl = if (uri.startsWith("http")) uri else File(uri).toURI().toString()
                println("Opening media: $fileOrUrl")

                // Create the new MediaPlayer
                val media = Media(fileOrUrl)
                MediaPlayer(media).also { player ->
                    mediaPlayer = player
                    setupMediaPlayer(player)
                    currentMediaView?.mediaPlayer = player
                }
            } catch (e: Exception) {
                handleError("Error opening media", e)
            }
        }
    }

    override fun play() {
        runOnFxThread {
            try {
                mediaPlayer?.play()
                _isPlaying = true
            } catch (e: Exception) {
                handleError("Error during playback", e)
            }
        }
    }

    override fun pause() {
        runOnFxThread {
            try {
                mediaPlayer?.pause()
                _isPlaying = false
            } catch (e: Exception) {
                handleError("Error during pause", e)
            }
        }
    }

    override fun stop() {
        stopMedia()
    }

    override fun seekTo(value: Float) {
        runOnFxThread {
            try {
                mediaPlayer?.let { player ->
                    if (_duration > 0) {
                        val targetTime = (value / 1000.0) * _duration
                        player.seek(Duration.seconds(targetTime))
                        _currentTime = targetTime
                    }
                }
            } catch (e: Exception) {
                handleError("Error during seek", e)
            }
        }
    }

    override fun dispose() {
        runOnFxThread {
            stopMedia()
            currentMediaView = null
        }
    }

    override fun clearError() {
        _error = null
    }

    // Helper private functions
    private fun setupMediaPlayer(player: MediaPlayer) {
        with(player) {
            // Initial setup
            volume = _volume.toDouble()
            cycleCount = if (_loop) MediaPlayer.INDEFINITE else 1

            // Audio spectrum setup
            audioSpectrumInterval = 0.05
            audioSpectrumThreshold = -60
            audioSpectrumNumBands = 2

            // Listeners
            setupAudioSpectrumListener(this)
            setupTimeListener(this)
            setupStatusListener(this)
            setupEventHandlers(this)
        }
    }

    private fun setupAudioSpectrumListener(player: MediaPlayer) {
        player.setAudioSpectrumListener { _, _, magnitudes, _ ->
            val leftDb = magnitudes[0]
            val rightDb = if (magnitudes.size > 1) magnitudes[1] else magnitudes[0]
            _leftLevel = ((leftDb + 60) / 60 * 100).coerceIn(0.0F, 100.0F)
            _rightLevel = ((rightDb + 60) / 60 * 100).coerceIn(0.0F, 100.0F)
        }
    }

    private fun setupTimeListener(player: MediaPlayer) {
        player.currentTimeProperty().addListener { _, _, newValue ->
            _currentTime = newValue.toSeconds()
            if (!userDragging && player.totalDuration != null) {
                val duration = player.totalDuration.toSeconds()
                if (duration > 0) {
                    _sliderPos = (_currentTime / duration * 1000).toFloat()
                }
            }
        }

        player.totalDurationProperty().addListener { _, _, newValue ->
            _duration = newValue?.toSeconds() ?: 0.0
        }
    }

    private fun setupStatusListener(player: MediaPlayer) {
        player.statusProperty().addListener { _, _, newStatus ->
            println("Player status: $newStatus")
            when (newStatus) {
                MediaPlayer.Status.PLAYING -> {
                    _isPlaying = true
                    _isLoading = false
                }
                MediaPlayer.Status.PAUSED -> {
                    _isPlaying = false
                    _isLoading = false
                }
                MediaPlayer.Status.STOPPED -> {
                    _isPlaying = false
                    _isLoading = false
                }
                MediaPlayer.Status.STALLED -> {
                    _isLoading = true
                }
                MediaPlayer.Status.UNKNOWN -> {
                    _isLoading = true
                    _isPlaying = false
                }
                MediaPlayer.Status.READY -> {
                    _isLoading = false
                }
                MediaPlayer.Status.HALTED -> {
                    _isPlaying = false
                    _isLoading = false
                    handleError("Player halted", null)
                }
                MediaPlayer.Status.DISPOSED -> {
                    _isPlaying = false
                    _isLoading = false
                }
            }
        }
    }

    private fun setupEventHandlers(player: MediaPlayer) {
        player.setOnReady {
            println("Player ready")
            _duration = player.totalDuration?.toSeconds() ?: 0.0
            _isLoading = false
            player.play()
        }

        player.setOnEndOfMedia {
            if (!_loop) {
                _isPlaying = false
            }
        }

        player.setOnError {
            handleError("Player error", player.error)
        }
    }

    private fun stopMedia() {
        runOnFxThread {
            try {
                mediaPlayer?.let { player ->
                    if (player.status != MediaPlayer.Status.DISPOSED) {
                        player.stop()
                        player.dispose()
                    }
                }
                mediaPlayer = null
                currentMediaView?.mediaPlayer = null
                resetStates()
                _error = null
                println("Media stopped successfully")
            } catch (e: Exception) {
                handleError("Error stopping media", e)
            }
        }
    }

    private fun resetStates() {
        _isPlaying = false
        _currentTime = 0.0
        _duration = 0.0
        _sliderPos = 0f
        _leftLevel = 0f
        _rightLevel = 0f
        _isLoading = false
    }

    private fun updateVolume() {
        runOnFxThread {
            mediaPlayer?.volume = _volume.toDouble()
        }
    }

    private fun updateLoopStatus() {
        runOnFxThread {
            mediaPlayer?.cycleCount = if (_loop) MediaPlayer.INDEFINITE else 1
        }
    }

    private fun handleSliderChange(value: Float) {
        if (_duration > 0) {
            _currentTime = (value / 1000.0) * _duration
            seekTo(value)
        }
    }

    private fun handleError(message: String, error: Throwable?) {
        println("$message: ${error?.message}")
        error?.printStackTrace()
        _isPlaying = false
        _isLoading = false

        _error = when {
            error?.message?.contains("codec", ignoreCase = true) == true ->
                VideoPlayerError.CodecError(error.message ?: message)

            error?.message?.contains("network", ignoreCase = true) == true ||
                    error?.message?.contains("connection", ignoreCase = true) == true ->
                VideoPlayerError.NetworkError(error.message ?: message)

            error?.message?.contains("source", ignoreCase = true) == true ||
                    error?.message?.contains("file", ignoreCase = true) == true ||
                    error?.message?.contains("uri", ignoreCase = true) == true ->
                VideoPlayerError.SourceError(error.message ?: message)

            else -> VideoPlayerError.UnknownError(error?.message ?: message)
        }
    }

    private fun formatTime(seconds: Double): String {
        val duration = ofSeconds(seconds.toLong())
        val hours = duration.toHours()
        val minutes = duration.toMinutesPart()
        val secs = duration.toSecondsPart()

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }

    private fun runOnFxThread(action: () -> Unit) {
        if (Platform.isFxApplicationThread()) {
            action()
        } else {
            Platform.runLater(action)
        }
    }
}