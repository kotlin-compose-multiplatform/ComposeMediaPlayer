package io.github.kdroidfilter.composemediaplayer

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.kdroid.androidcontextprovider.ContextProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Stable
actual open class VideoPlayerState {
    private val context = ContextProvider.getContext()
    internal var exoPlayer: ExoPlayer? = null
    private var updateJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val audioProcessor = AudioLevelProcessor()

    // Volume control
    private var _volume by mutableStateOf(1f)
    actual var volume: Float
        get() = _volume
        set(value) {
            _volume = value.coerceIn(0f, 1f)
            exoPlayer?.volume = _volume
        }

    // Slider position
    private var _sliderPos by mutableStateOf(0f)
    actual var sliderPos: Float
        get() = _sliderPos
        set(value) {
            _sliderPos = value.coerceIn(0f, 1000f)
            if (!userDragging) {
                seekTo(value)
            }
        }

    // Loop control
    private var _loop by mutableStateOf(false)
    actual var loop: Boolean
        get() = _loop
        set(value) {
            _loop = value
            exoPlayer?.repeatMode = if (value) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        }

    // State variables
    actual var userDragging by mutableStateOf(false)
    private var _isPlaying by mutableStateOf(false)
    actual val isPlaying: Boolean get() = _isPlaying

    // Audio levels
    private var _leftLevel by mutableStateOf(0f)
    private var _rightLevel by mutableStateOf(0f)
    actual val leftLevel: Float get() = _leftLevel
    actual val rightLevel: Float get() = _rightLevel

    // Time tracking
    private var _currentTime by mutableStateOf(0.0)
    private var _duration by mutableStateOf(0.0)
    actual val positionText: String get() = formatTime(_currentTime)
    actual val durationText: String get() = formatTime(_duration)

    init {
        audioProcessor.setOnAudioLevelUpdateListener { left, right ->
            _leftLevel = left
            _rightLevel = right
        }
        initializePlayer()
    }

    private fun initializePlayer() {
        val audioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(audioProcessor))
            .build()

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return audioSink
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        exoPlayer = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .build()
            .apply {
                addListener(createPlayerListener())
                volume = _volume
            }
    }

    private fun createPlayerListener() = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    exoPlayer?.let { player ->
                        _duration = player.duration.toDouble() / 1000.0
                        _isPlaying = player.isPlaying
                        if (player.isPlaying) startPositionUpdates()
                    }
                }
                Player.STATE_ENDED -> {
                    stopPositionUpdates()
                    _isPlaying = false
                }
                Player.STATE_IDLE, Player.STATE_BUFFERING -> {
                    // Handle other states if needed
                }
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying = playing
            if (playing) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        updateJob = coroutineScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.playbackState == Player.STATE_READY) {
                        _currentTime = player.currentPosition.toDouble() / 1000.0
                        if (!userDragging && _duration > 0) {
                            _sliderPos = (_currentTime / _duration * 1000).toFloat()
                        }
                    }
                }
                delay(16) // ~60fps update rate
            }
        }
    }

    private fun stopPositionUpdates() {
        updateJob?.cancel()
        updateJob = null
    }

    actual fun openUri(uri: String) {
        exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
            try {
                val mediaItem = MediaItem.fromUri(uri)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.volume = volume
                player.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                player.play()
            } catch (e: Exception) {
                println("Error opening media: ${e.message}")
                _isPlaying = false
            }
        }
    }

    actual fun play() {
        exoPlayer?.play()
    }

    actual fun pause() {
        exoPlayer?.pause()
    }

    actual fun stop() {
        exoPlayer?.stop()
        resetStates()
    }

    actual fun seekTo(value: Float) {
        if (_duration > 0) {
            val targetTime = (value / 1000.0) * _duration
            exoPlayer?.seekTo((targetTime * 1000).toLong())
        }
    }

    private fun resetStates() {
        _currentTime = 0.0
        _duration = 0.0
        _sliderPos = 0f
        _leftLevel = 0f
        _rightLevel = 0f
        _isPlaying = false
    }

    actual fun dispose() {
        stopPositionUpdates()
        coroutineScope.cancel()
        exoPlayer?.let { player ->
            player.stop()
            player.release()
        }
        exoPlayer = null
        resetStates()
    }

    private fun formatTime(seconds: Double): String {
        val hours = (seconds / 3600).toInt()
        val minutes = ((seconds % 3600) / 60).toInt()
        val secs = (seconds % 60).toInt()

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
    }
}