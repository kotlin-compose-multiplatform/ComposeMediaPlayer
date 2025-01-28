package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Stable

/**
 * Represents a single subtitle track for a video.
 *
 * This class contains information about a subtitle track, including an identifier,
 * the language of the subtitles, and a user-friendly name for display.
 *
 * Subtitle tracks are typically used in video playback systems to provide captions
 * in multiple languages or for accessibility purposes.
 *
 * @property id A unique identifier for the subtitle track.
 * @property language The language of the subtitle track represented as a string (e.g., "en" for English).
 * @property name The display name of the subtitle track for user selection.
 */
@Stable
data class SubtitleTrack(
    val id: String,
    val language: String,
    val name: String,
    val src: String,
)