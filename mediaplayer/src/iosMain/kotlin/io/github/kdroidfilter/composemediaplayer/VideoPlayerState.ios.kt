package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Stable
import io.github.vinceglb.filekit.PlatformFile

@Stable
actual open class VideoPlayerState {
    //TODO
    actual val hasMedia: Boolean = false
    actual val isPlaying: Boolean = false
    actual var volume: Float = 1.0f
    actual var sliderPos: Float = 0.0f
    actual var userDragging: Boolean = false
    actual var loop: Boolean = false
    actual val leftLevel: Float = 0.0f
    actual val rightLevel: Float = 0.0f
    actual val positionText: String = "00:00"
    actual val durationText: String = "00:00"
    actual val isLoading = false
    actual  val error: VideoPlayerError? = null

    private var _metadata = VideoMetadata()
    actual val metadata: VideoMetadata get() = _metadata

    actual var subtitlesEnabled = false
    actual var currentSubtitleTrack : SubtitleTrack? = null
    actual val availableSubtitleTracks = mutableListOf<SubtitleTrack>()
    actual fun selectSubtitleTrack(track: SubtitleTrack?){}
    actual fun disableSubtitles() {}

    actual fun openUri(uri: String) {
    }

    actual fun openFile(file: PlatformFile) {
    }

    actual fun play() {
    }

    actual fun pause() {
    }

    actual fun stop() {
    }

    actual fun seekTo(value: Float) {
    }

    actual fun hideMedia() {}

    actual fun showMedia() {}

    actual fun dispose() {
    }

    actual fun clearError() {}
}
