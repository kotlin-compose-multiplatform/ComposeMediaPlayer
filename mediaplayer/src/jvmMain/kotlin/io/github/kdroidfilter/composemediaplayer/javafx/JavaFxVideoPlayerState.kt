package io.github.kdroidfilter.composemediaplayer.javafx
import io.github.kdroidfilter.composemediaplayer.PlatformVideoPlayerState
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class MediaState(
    val currentTime: Double = 0.0,
    val duration: Double = 0.0,
    val isPlaying: Boolean = false,
    val hasMedia: Boolean = false,
    val volume: Double = 1.0
)

class JavaFxVideoPlayerState : PlatformVideoPlayerState {
    private var currentMediaView: MediaView? = null
    internal var mediaPlayer: MediaPlayer? = null

    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState: StateFlow<MediaState> = _mediaState.asStateFlow()

    // Override from VideoPlayerState
    override val isPlaying: Boolean
        get() = _mediaState.value.isPlaying

    override var volume: Float
        get() = _mediaState.value.volume.toFloat()
        set(value) {
            val clampedVolume = value.coerceIn(0.0f..1.0f)
            mediaPlayer?.volume = clampedVolume.toDouble()
            updateState { it.copy(volume = clampedVolume.toDouble()) }
        }

    override var sliderPos: Float = 0f
        get() = if (_mediaState.value.duration > 0)
            (_mediaState.value.currentTime / _mediaState.value.duration).toFloat()
        else 0f
        set(value) {
            field = value
            if (!userDragging) {
                seekToPercent(value * 100.0)
            }
        }

    override var userDragging: Boolean = false

    override var loop: Boolean = false
        set(value) {
            field = value
            mediaPlayer?.cycleCount = if (value) MediaPlayer.INDEFINITE else 1
        }

    // Audio levels - JavaFX doesn't provide direct audio level monitoring
    override val leftLevel: Float = 0f
    override val rightLevel: Float = 0f

    override val positionText: String
        get() = formatTime(_mediaState.value.currentTime)

    override val durationText: String
        get() = formatTime(_mediaState.value.duration)

    fun updateMediaView(view: MediaView) {
        currentMediaView = view
        mediaPlayer?.let { player ->
            Platform.runLater {
                currentMediaView?.mediaPlayer = player
            }
        }
    }

    override fun openUri(uri: String) {
        stopMedia()
        val fileOrUrl = if (uri.startsWith("http")) uri else File(uri).toURI().toString()

        try {
            val media = Media(fileOrUrl)
            mediaPlayer = MediaPlayer(media).apply {
                volume = _mediaState.value.volume
                cycleCount = if (loop) MediaPlayer.INDEFINITE else 1

                currentTimeProperty().addListener { _, _, newValue ->
                    updateState {
                        it.copy(currentTime = newValue.toSeconds())
                    }
                }

                totalDurationProperty().addListener { _, _, newValue ->
                    updateState {
                        it.copy(duration = newValue?.toSeconds() ?: 0.0)
                    }
                }

                statusProperty().addListener { _, _, newStatus ->
                    updateState {
                        it.copy(
                            isPlaying = newStatus == MediaPlayer.Status.PLAYING,
                            hasMedia = true
                        )
                    }
                }

                setOnReady {
                    Platform.runLater {
                        currentMediaView?.mediaPlayer = this
                        updateState {
                            it.copy(
                                duration = totalDuration?.toSeconds() ?: 0.0,
                                hasMedia = true
                            )
                        }
                    }
                }

                setOnEndOfMedia {
                    if (!loop) {
                        updateState {
                            it.copy(isPlaying = false)
                        }
                    }
                }

                setOnError {
                    println("MediaPlayer Error: ${error.message}")
                    error.printStackTrace()
                    updateState {
                        it.copy(hasMedia = false, isPlaying = false)
                    }
                }
            }
            play()
        } catch (e: Exception) {
            println("Error opening media: ${e.message}")
            e.printStackTrace()
            updateState {
                it.copy(hasMedia = false, isPlaying = false)
            }
        }
    }

    override fun play() {
        mediaPlayer?.play()
        updateState { it.copy(isPlaying = true) }
    }

    override fun pause() {
        mediaPlayer?.pause()
        updateState { it.copy(isPlaying = false) }
    }

    override fun stop() {
        stopMedia()
    }

    override fun seekTo(value: Float) {
        seekToPercent(value * 100.0)
    }

    private fun stopMedia() {
        mediaPlayer?.stop()
        mediaPlayer?.dispose()
        mediaPlayer = null
        updateState {
            MediaState()  // Reset to initial state
        }
    }

    private fun seekToPercent(percent: Double) {
        val duration = _mediaState.value.duration
        if (duration > 0) {
            val targetTime = (percent.coerceIn(0.0, 100.0) / 100.0) * duration
            mediaPlayer?.seek(Duration.seconds(targetTime))
        }
    }

    private fun updateState(update: (MediaState) -> MediaState) {
        _mediaState.value = update(_mediaState.value)
    }

    override fun dispose() {
        stopMedia()
        currentMediaView = null
    }

    private fun formatTime(seconds: Double): String {
        val duration = java.time.Duration.ofSeconds(seconds.toLong())
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