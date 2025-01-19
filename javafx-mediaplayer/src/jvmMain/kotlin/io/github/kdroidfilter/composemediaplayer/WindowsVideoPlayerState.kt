
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

class WindowsVideoPlayerState {
    private var currentMediaView: MediaView? = null
    internal var mediaPlayer: MediaPlayer? = null

    private val _mediaState = MutableStateFlow(MediaState())
    val mediaState: StateFlow<MediaState> = _mediaState.asStateFlow()

    var volume: Double
        get() = _mediaState.value.volume
        set(value) {
            val clampedVolume = value.coerceIn(0.0..1.0)
            mediaPlayer?.volume = clampedVolume
            updateState { it.copy(volume = clampedVolume) }
        }

    private var loop: Boolean = false
        set(value) {
            field = value
            mediaPlayer?.cycleCount = if (value) MediaPlayer.INDEFINITE else 1
        }

    fun updateMediaView(view: MediaView) {
        currentMediaView = view
        mediaPlayer?.let { player ->
            Platform.runLater {
                currentMediaView?.mediaPlayer = player
            }
        }
    }

    fun openMedia(uri: String) {
        stopMedia()
        val fileOrUrl = if (uri.startsWith("http")) uri else File(uri).toURI().toString()

        try {
            val media = Media(fileOrUrl)
            mediaPlayer = MediaPlayer(media).apply {
                volume = _mediaState.value.volume
                cycleCount = if (loop) MediaPlayer.INDEFINITE else 1

                // Mise à jour du temps actuel
                currentTimeProperty().addListener { _, _, newValue ->
                    updateState {
                        it.copy(currentTime = newValue.toSeconds())
                    }
                }

                // Mise à jour de la durée totale
                totalDurationProperty().addListener { _, _, newValue ->
                    updateState {
                        it.copy(duration = newValue?.toSeconds() ?: 0.0)
                    }
                }

                // Mise à jour du statut de lecture
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
                    println("Erreur MediaPlayer: ${error.message}")
                    error.printStackTrace()
                    updateState {
                        it.copy(hasMedia = false, isPlaying = false)
                    }
                }
            }
            playMedia()
        } catch (e: Exception) {
            println("Erreur lors de l'ouverture du média: ${e.message}")
            e.printStackTrace()
            updateState {
                it.copy(hasMedia = false, isPlaying = false)
            }
        }
    }

    fun playMedia() {
        mediaPlayer?.play()
        updateState { it.copy(isPlaying = true) }
    }

    fun pauseMedia() {
        mediaPlayer?.pause()
        updateState { it.copy(isPlaying = false) }
    }

    fun stopMedia() {
        mediaPlayer?.stop()
        mediaPlayer?.dispose()
        mediaPlayer = null
        updateState {
            MediaState()  // Reset to initial state
        }
    }

    fun seekTo(position: Double) {
        mediaPlayer?.seek(Duration.seconds(position))
    }

    fun seekToPercent(percent: Double) {
        val duration = _mediaState.value.duration
        if (duration > 0) {
            val targetTime = (percent.coerceIn(0.0, 100.0) / 100.0) * duration
            seekTo(targetTime)
        }
    }

    private fun updateState(update: (MediaState) -> MediaState) {
        _mediaState.value = update(_mediaState.value)
    }

    fun togglePlayPause() {
        if (_mediaState.value.isPlaying) {
            pauseMedia()
        } else {
            playMedia()
        }
    }

    fun dispose() {
        stopMedia()
        currentMediaView = null
    }
}