package io.github.kdroidfilter.composemediaplayer.javafx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.composemediaplayer.PlatformVideoPlayerState
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import java.io.File
import java.time.Duration.ofSeconds

// JavaFX Video Player State implementation
class JavaFxVideoPlayerState : PlatformVideoPlayerState {
    private var currentMediaView: MediaView? = null
    internal var mediaPlayer: MediaPlayer? = null

    // Compose states with backing properties for custom logic
    private var _volume by mutableStateOf(1f)
    override var volume: Float
        get() = _volume
        set(value) {
            _volume = value.coerceIn(0f, 1f) // Ensure volume stays within bounds
            mediaPlayer?.volume = _volume.toDouble()
        }

    private var _sliderPos by mutableStateOf(0f)
    override var sliderPos: Float
        get() = _sliderPos
        set(value) {
            _sliderPos = value
            if (!userDragging) {
                seekTo(value)
            }
        }

    private var _loop by mutableStateOf(false)
    override var loop: Boolean
        get() = _loop
        set(value) {
            _loop = value
            mediaPlayer?.cycleCount = if (value) MediaPlayer.INDEFINITE else 1 // Loop setting
        }

    override var userDragging by mutableStateOf(false)
    private var _isPlaying by mutableStateOf(false)
    override val isPlaying: Boolean get() = _isPlaying

    private var _leftLevel by mutableStateOf(0f)
    private var _rightLevel by mutableStateOf(0f)
    override val leftLevel: Float get() = _leftLevel
    override val rightLevel: Float get() = _rightLevel

    private var _currentTime by mutableStateOf(0.0)
    private var _duration by mutableStateOf(0.0)
    override val positionText: String get() = formatTime(_currentTime)
    override val durationText: String get() = formatTime(_duration)

    // Updates the MediaView with the current MediaPlayer
    fun updateMediaView(view: MediaView) {
        currentMediaView = view
        mediaPlayer?.let { player ->
            Platform.runLater {
                currentMediaView?.mediaPlayer = player
            }
        }
    }

    // Opens the media file or URL and initializes the media player
    override fun openUri(uri: String) {
        stopMedia()
        val fileOrUrl = if (uri.startsWith("http")) uri else File(uri).toURI().toString()

        try {
            val media = Media(fileOrUrl)
            mediaPlayer = MediaPlayer(media).apply {
                // Initial configuration
                volume = _volume.toDouble()
                cycleCount = if (loop) MediaPlayer.INDEFINITE else 1

                // Configuring AudioSpectrum for audio levels
                audioSpectrumInterval = 0.05
                audioSpectrumThreshold = -60
                audioSpectrumNumBands = 2

                setAudioSpectrumListener { _, _, magnitudes, _ ->
                    val leftDb = magnitudes[0]
                    val rightDb = if (magnitudes.size > 1) magnitudes[1] else magnitudes[0]

                    val leftPercent = ((leftDb + 60) / 60 * 100).coerceIn(0.0F, 100.0F)
                    val rightPercent = ((rightDb + 60) / 60 * 100).coerceIn(0.0F, 100.0F)

                    // Update the audio levels on the UI thread
                    Platform.runLater {
                        _leftLevel = leftPercent
                        _rightLevel = rightPercent
                    }
                }

                // Listeners for time updates
                currentTimeProperty().addListener { _, _, newValue ->
                    _currentTime = newValue.toSeconds()
                    if (!userDragging) {
                        val duration = totalDuration?.toSeconds() ?: 0.0
                        if (duration > 0) {
                            _sliderPos = (_currentTime / duration * 1000).toFloat()
                        }
                    }
                }

                totalDurationProperty().addListener { _, _, newValue ->
                    _duration = newValue?.toSeconds() ?: 0.0
                }

                // Listeners for playback status
                statusProperty().addListener { _, _, newStatus ->
                    _isPlaying = newStatus == MediaPlayer.Status.PLAYING
                }

                setOnReady {
                    Platform.runLater {
                        currentMediaView?.mediaPlayer = this
                        _duration = totalDuration?.toSeconds() ?: 0.0
                    }
                }

                setOnEndOfMedia {
                    if (!loop) {
                        _isPlaying = false
                    }
                }

                setOnError {
                    println("MediaPlayer Error: ${error.message}")
                    error.printStackTrace()
                    _isPlaying = false
                }
            }
            play()
        } catch (e: Exception) {
            println("Error opening media: ${e.message}")
            e.printStackTrace()
            _isPlaying = false
        }
    }

    // Starts playing the media
    override fun play() {
        mediaPlayer?.play()
        _isPlaying = true
    }

    // Pauses the media
    override fun pause() {
        mediaPlayer?.pause()
        _isPlaying = false
    }

    // Stops and disposes of the media player
    override fun stop() {
        stopMedia()
    }

    // Seeks to a specific position in the media
    override fun seekTo(value: Float) {
        if (_duration > 0) {
            val targetTime = (value / 1000.0) * _duration
            mediaPlayer?.seek(Duration.seconds(targetTime))
        }
    }

    // Stops and disposes of the media player, resetting states
    private fun stopMedia() {
        mediaPlayer?.stop()
        mediaPlayer?.dispose()
        mediaPlayer = null
        resetStates()
    }

    // Resets the player state
    private fun resetStates() {
        _isPlaying = false
        _currentTime = 0.0
        _duration = 0.0
        _sliderPos = 0f
        _leftLevel = 0f
        _rightLevel = 0f
    }

    // Cleans up the media player and view
    override fun dispose() {
        stopMedia()
        currentMediaView = null
    }

    // Formats time in HH:mm:ss or mm:ss format
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
}
