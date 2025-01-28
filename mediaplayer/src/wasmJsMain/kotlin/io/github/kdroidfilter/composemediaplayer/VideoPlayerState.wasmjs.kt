package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.composemediaplayer.util.formatTime
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.*
import kotlinx.io.IOException
import org.w3c.dom.url.URL
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Stable
actual open class VideoPlayerState {

    private val playerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastUpdateTime = TimeSource.Monotonic.markNow()

    private var _sourceUri by mutableStateOf<String?>(null)
    val sourceUri: String? get() = _sourceUri

    private var _isPlaying by mutableStateOf(false)
    actual val isPlaying: Boolean get() = _isPlaying

    private var _hasMedia by mutableStateOf(false)
    actual val hasMedia: Boolean get() = _hasMedia

    internal var _isLoading by mutableStateOf(false)
    actual val isLoading: Boolean get() = _isLoading

    private var _error by mutableStateOf<VideoPlayerError?>(null)
    actual val error: VideoPlayerError? get() = _error

    actual val metadata = VideoMetadata()

    actual var subtitlesEnabled = false
    actual var currentSubtitleTrack : SubtitleTrack? = null
    actual val availableSubtitleTracks  = emptyList<SubtitleTrack>()
    actual fun selectSubtitleTrack(track: SubtitleTrack?){}
    actual fun disableSubtitles() {}

    actual var volume by mutableStateOf(1.0f)
    actual var sliderPos by mutableStateOf(0.0f)
    actual var userDragging by mutableStateOf(false)
    actual var loop by mutableStateOf(false)

    private var _leftLevel by mutableStateOf(0f)
    private var _rightLevel by mutableStateOf(0f)
    actual val leftLevel: Float get() = _leftLevel
    actual val rightLevel: Float get() = _rightLevel

    private var _positionText by mutableStateOf("00:00")
    private var _durationText by mutableStateOf("00:00")
    actual  val positionText: String get() = _positionText
    actual val durationText: String get() = _durationText

    internal var seekJob: Job? = null

    actual fun openUri(uri: String) {
        playerScope.coroutineContext.cancelChildren()

        _sourceUri = uri
        _hasMedia = true
        _isLoading = true  // Set initial loading state
        _error = null
        _isPlaying = false

        // Don't set isLoading to false here - let the video events handle it
        playerScope.launch {
            try {
                _isPlaying = true
            } catch (e: Exception) {
                _isLoading = false
                _error = when (e) {
                    is IOException -> VideoPlayerError.NetworkError(e.message ?: "Network error")
                    else -> VideoPlayerError.UnknownError(e.message ?: "Unknown error")
                }
            }
        }
    }

    actual fun openFile(file: PlatformFile) {
        val fileUri = file.toUriString()
        openUri(fileUri)
    }

    actual  fun play() {
        if (_hasMedia && !_isPlaying) {
            _isPlaying = true
        }
    }

    actual  fun pause() {
        if (_isPlaying) {
            _isPlaying = false
        }
    }

    actual fun stop() {
        _isPlaying = false
        _sourceUri = null
        _hasMedia = false
        _isLoading = false
        sliderPos = 0.0f
        _positionText = "00:00"
        _durationText = "00:00"
    }

    actual  fun seekTo(value: Float) {
        sliderPos = value
        seekJob?.cancel()
    }

    actual fun clearError() {
        _error = null
    }

    fun updateAudioLevels(left: Float, right: Float) {
        _leftLevel = left
        _rightLevel = right
    }

    private var _currentDuration: Float = 0f

    fun updatePosition(currentTime: Float, duration: Float) {
        val now = TimeSource.Monotonic.markNow()
        if (now - lastUpdateTime >= 1.seconds) {
            _positionText = if (currentTime.isNaN()) "00:00" else formatTime(currentTime)
            _durationText = if (duration.isNaN()) "00:00" else formatTime(duration)

            if (!userDragging && duration > 0f && !duration.isNaN()) {
                sliderPos = (currentTime / duration) * PERCENTAGE_MULTIPLIER
            }
            _currentDuration = duration
            lastUpdateTime = now
        }
    }

    fun onTimeUpdate(currentTime: Float, duration: Float) {
        updatePosition(currentTime, duration)
    }

    actual fun dispose() {
        playerScope.cancel()
    }

    companion object {
        internal const val PERCENTAGE_MULTIPLIER = 1000f
    }
}

fun PlatformFile.toUriString(): String {
    return URL.createObjectURL(this.file)
}
