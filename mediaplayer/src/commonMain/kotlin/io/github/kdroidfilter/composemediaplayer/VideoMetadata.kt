package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Stable

/**
 * Represents metadata information for a video file.
 * All properties are nullable as metadata might not be available for all video sources.
 */
@Stable
data class VideoMetadata(
    var title: String? = null,
    var artist: String? = null,
    var duration: Long? = null, // Duration in milliseconds
    var width: Int? = null,
    var height: Int? = null,
    var bitrate: Long? = null, // Bitrate in bits per second
    var frameRate: Float? = null,
    var mimeType: String? = null,
    var audioChannels: Int? = null,
    var audioSampleRate: Int? = null,
)
