@file:OptIn(ExperimentalForeignApi::class)
package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.*
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.dispatch_get_main_queue
import io.github.kdroidfilter.composemediaplayer.util.formatTime
import io.github.vinceglb.filekit.PlatformFile

@Stable
actual open class VideoPlayerState {
    // États de base
    actual var volume: Float = 1.0f
    actual var sliderPos: Float by mutableStateOf(0f) // valeur entre 0 et 1000
    actual var userDragging: Boolean = false
    actual var loop: Boolean = false

    // États de lecture
    actual val hasMedia: Boolean get() = _hasMedia
    actual val isPlaying: Boolean get() = _isPlaying

    private var _hasMedia by mutableStateOf(false)
    private var _isPlaying by mutableStateOf(false)

    // Textes affichant la position et la durée
    private var _positionText: String by mutableStateOf("00:00")
    actual val positionText: String get() = _positionText
    private var _durationText: String by mutableStateOf("00:00")
    actual val durationText: String get() = _durationText

    actual val isLoading: Boolean = false
    actual val error: VideoPlayerError? = null

    // Instance observable du player
    var player: AVPlayer? by mutableStateOf(null)
        private set

    // Observateur périodique pour mettre à jour le temps
    private var timeObserverToken: Any? = null

    // Valeurs internes de temps (en secondes)
    private var _currentTime: Double = 0.0
    private var _duration: Double = 0.0

    // Niveaux audio (non implémentés pour l’instant)
    actual val leftLevel: Float = 0f
    actual val rightLevel: Float = 0f

    /**
     * Démarre un observateur périodique (≈60 fps) pour mettre à jour la progression, la position et la durée.
     */
    private fun startPositionUpdates() {
        stopPositionUpdates() // Nettoyage éventuel d'un observateur existant
        val interval = CMTimeMakeWithSeconds(1.0 / 60.0, 600) // environ 60fps
        timeObserverToken = player?.addPeriodicTimeObserverForInterval(
            interval = interval,
            queue = dispatch_get_main_queue(),
            usingBlock = { time ->
                val currentSeconds = CMTimeGetSeconds(time)
                val durationSeconds = player?.currentItem?.duration?.let { CMTimeGetSeconds(it) } ?: 0.0
                _currentTime = currentSeconds
                _duration = durationSeconds

                if (!userDragging && durationSeconds > 0) {
                    sliderPos = ((currentSeconds / durationSeconds) * 1000).toFloat()
                }
                _positionText = formatTime(currentSeconds.toFloat())
                _durationText = formatTime(durationSeconds.toFloat())

                // Mode boucle : si la fin est atteinte, revenir au début et relancer la lecture
                if (loop && durationSeconds > 0 && currentSeconds >= durationSeconds) {
                    player?.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
                    player?.play()
                }
            }
        )
    }

    private fun stopPositionUpdates() {
        timeObserverToken?.let { token ->
            player?.removeTimeObserver(token)
            timeObserverToken = null
        }
    }

    /**
     * Ouvre une URI vidéo.
     * Crée un AVPlayer avec un AVPlayerItem, démarre l’observateur et lance la lecture.
     */
    actual fun openUri(uri: String) {
        println("[VideoPlayerState] openUri appelé avec uri: $uri")
        val nsUrl = NSURL.URLWithString(uri) ?: run {
            println("[VideoPlayerState] Échec de création d'NSURL depuis uri: $uri")
            return
        }
        // Nettoyage
        stopPositionUpdates()
        player?.pause()

        // Création d'un nouvel AVPlayerItem et AVPlayer
        val playerItem = AVPlayerItem(nsUrl)
        player = AVPlayer(playerItem = playerItem).apply {
            volume = this@VideoPlayerState.volume
        }
        // Démarrer l'observateur et marquer la présence de média
        startPositionUpdates()
        _hasMedia = true
        // Lancer la lecture automatiquement
        play()
    }

    /**
     * Ouvre un fichier vidéo.
     * La conversion du fichier en URI est effectuée via toString().
     */

    actual fun play() {
        println("[VideoPlayerState] play appelé")
        if (player == null) {
            println("[VideoPlayerState] play: player est null")
            return
        }
        player?.play()
        _isPlaying = true
    }

    actual fun pause() {
        println("[VideoPlayerState] pause appelé")
        player?.pause()
        _isPlaying = false
    }

    actual fun stop() {
        println("[VideoPlayerState] stop appelé")
        player?.pause()
        player?.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
        _isPlaying = false
    }

    /**
     * Recherche la position cible à partir d'une valeur comprise entre 0 et 1000, puis déplace la lecture.
     */
    actual fun seekTo(value: Float) {
        if (_duration > 0) {
            val targetTime = _duration * (value / 1000.0)
            player?.seekToTime(CMTimeMakeWithSeconds(targetTime, 1))
        }
    }

    actual fun hideMedia() {
        println("[VideoPlayerState] hideMedia appelé")
        _hasMedia = false
    }

    actual fun showMedia() {
        println("[VideoPlayerState] showMedia appelé")
        _hasMedia = true
    }

    actual fun clearError() {
        println("[VideoPlayerState] clearError appelé")
        // Pas de gestion d'erreur dans cette version de base
    }

    actual fun dispose() {
        println("[VideoPlayerState] dispose appelé")
        stopPositionUpdates()
        player?.pause()
        player = null
        _hasMedia = false
        _isPlaying = false
    }

    actual fun openFile(file: PlatformFile) {
        println("[VideoPlayerState] openFile appelé avec file: $file")
        openUri(file.toString())
    }

    actual val metadata: VideoMetadata
        get() = TODO("Not yet implemented")
    actual var subtitlesEnabled: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    actual var currentSubtitleTrack: SubtitleTrack?
        get() = TODO("Not yet implemented")
        set(value) {}
    actual val availableSubtitleTracks: MutableList<SubtitleTrack>
        get() = TODO("Not yet implemented")

    actual fun selectSubtitleTrack(track: SubtitleTrack?) {
    }

    actual fun disableSubtitles() {
    }
}
