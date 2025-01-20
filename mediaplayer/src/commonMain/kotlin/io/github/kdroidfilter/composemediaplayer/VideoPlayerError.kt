package io.github.kdroidfilter.composemediaplayer

/**
 * Represents different types of errors that can occur during video playback
 */
sealed class VideoPlayerError {
    data class CodecError(val message: String): VideoPlayerError()
    data class NetworkError(val message: String): VideoPlayerError()
    data class SourceError(val message: String): VideoPlayerError()
    data class UnknownError(val message: String): VideoPlayerError()
}