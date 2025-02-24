// VideoPlayerState.kt
@file:OptIn(ExperimentalForeignApi::class, ExperimentalForeignApi::class, ExperimentalForeignApi::class)
package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

@Stable
actual open class VideoPlayerState {
    // États basiques
    actual var volume: Float = 1.0f
    actual var sliderPos: Float = 0.0f
    actual var userDragging: Boolean = false
    actual var loop: Boolean = false

    // États de lecture
    actual val hasMedia: Boolean get() = _hasMedia
    actual val isPlaying: Boolean get() = _isPlaying
    actual val leftLevel: Float = 0.0f
    actual val rightLevel: Float = 0.0f
    actual val positionText: String = "00:00"
    actual val durationText: String = "00:00"
    actual val isLoading: Boolean = false
    actual val error: VideoPlayerError? = null

    private var _hasMedia = false
    private var _isPlaying = false

    // Rendre le player observable par Compose
    var player: AVPlayer? by mutableStateOf(null)
        private set

    // Métadonnées et sous-titres (non implémentés ici)
    // ...

    actual fun openUri(uri: String) {
        println("[VideoPlayerState] openUri called with uri: $uri")
        val nsUrl = NSURL.URLWithString(uri) ?: run {
            println("[VideoPlayerState] Failed to create NSURL from uri: $uri")
            return
        }

        // Pause any existing player
        player?.pause()

        // Create an AVPlayerItem first
        val playerItem = AVPlayerItem(uRL = nsUrl)

        // Create a new player
        player = AVPlayer(playerItem = playerItem)

        // Set initial volume
        player?.volume = volume

        _hasMedia = true
        println("[VideoPlayerState] AVPlayer created successfully with uri: $uri")
    }

    actual fun openFile(file: PlatformFile) {
        println("[VideoPlayerState] openFile called with file: $file")
        openUri(file.toString())
    }

    actual fun play() {
        println("[VideoPlayerState] play called")
        if (player == null) {
            println("[VideoPlayerState] play: player is null")
        } else {
            player?.play()
            println("[VideoPlayerState] play: player started")
        }
        _isPlaying = true
    }

    actual fun pause() {
        println("[VideoPlayerState] pause called")
        if (player == null) {
            println("[VideoPlayerState] pause: player is null")
        } else {
            player?.pause()
            println("[VideoPlayerState] pause: player paused")
        }
        _isPlaying = false
    }

    actual fun stop() {
        println("[VideoPlayerState] stop called")
        if (player == null) {
            println("[VideoPlayerState] stop: player is null")
        } else {
            player?.pause()
            player?.seekToTime(CMTimeMakeWithSeconds(0.0, preferredTimescale = 1))
            println("[VideoPlayerState] stop: player stopped and reset to start")
        }
        _isPlaying = false
    }

    actual fun seekTo(value: Float) {
        println("[VideoPlayerState] seekTo called with value: $value")
        player?.seekToTime(CMTimeMakeWithSeconds(value.toDouble(), preferredTimescale = 1))
    }

    actual fun hideMedia() {
        println("[VideoPlayerState] hideMedia called")
        _hasMedia = false
    }

    actual fun showMedia() {
        println("[VideoPlayerState] showMedia called")
        _hasMedia = true
    }

    actual fun dispose() {
        println("[VideoPlayerState] dispose called")
        if (player == null) {
            println("[VideoPlayerState] dispose: player is null")
        } else {
            player?.pause()
            println("[VideoPlayerState] dispose: player paused and disposed")
        }
        player = null
        _hasMedia = false
        _isPlaying = false
    }

    actual fun clearError() {
        println("[VideoPlayerState] clearError called")
        // Pas de gestion d'erreur dans cette version basique
    }

    // Méthode utilitaire pour fournir l'instance du player à la vue Compose
    fun getPlayer(): AVPlayer? {
        println("[VideoPlayerState] getPlayer called, returning: $player")
        return player
    }

    actual val metadata: VideoMetadata = VideoMetadata()
    actual var subtitlesEnabled: Boolean = false
    actual var currentSubtitleTrack: SubtitleTrack? = null
    actual val availableSubtitleTracks: MutableList<SubtitleTrack> = mutableListOf()

    actual fun selectSubtitleTrack(track: SubtitleTrack?) {
    }

    actual fun disableSubtitles() {
    }
}
