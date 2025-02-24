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

@OptIn(ExperimentalForeignApi::class)
@Stable
actual open class VideoPlayerState {
    // États de base
    private var _volume = mutableStateOf(1.0f)
    actual var volume: Float
        get() = _volume.value
        set(value) {
            _volume.value = value
            if (_isPlaying) {
                player?.volume = value
            }
        }

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

    // Observateur périodique pour mettre à jour la position (≈60 fps)
    private var timeObserverToken: Any? = null

    // Observateur pour la notification de fin de lecture
    private var endObserver: Any? = null

    // Valeurs internes de temps (en secondes)
    private var _currentTime: Double = 0.0
    private var _duration: Double = 0.0

    // Niveaux audio (non implémentés pour l’instant)
    actual val leftLevel: Float = 0f
    actual val rightLevel: Float = 0f

    /**
     * Démarre un observateur périodique pour mettre à jour la progression.
     */
    private fun startPositionUpdates() {
        stopPositionUpdates() // Nettoyage d'un observateur existant
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
     * Supprime l'observateur de notification de fin de lecture.
     */
    private fun removeEndObserver() {
        endObserver?.let {
            NSNotificationCenter.defaultCenter.removeObserver(it)
            endObserver = null
        }
    }

    /**
     * Ouvre une URI vidéo.
     * Crée un AVPlayer avec un AVPlayerItem, démarre les observateurs et lance la lecture.
     */
    actual fun openUri(uri: String) {
        println("[VideoPlayerState] openUri appelé avec uri: $uri")
        val nsUrl = NSURL.URLWithString(uri) ?: run {
            println("[VideoPlayerState] Échec de création d'NSURL depuis uri: $uri")
            return
        }

        // Nettoyage d'un média précédent
        stopPositionUpdates()
        removeEndObserver()
        player?.pause()

        // Création d'un nouvel AVPlayerItem et AVPlayer
        val playerItem = AVPlayerItem(nsUrl)
        player = AVPlayer(playerItem = playerItem).apply {
            volume = this@VideoPlayerState.volume
            // On désactive l'action par défaut à la fin pour gérer nous-mêmes le comportement
            actionAtItemEnd = AVPlayerActionAtItemEndNone
        }

        // Ajouter l'observateur pour détecter la fin du média et gérer le comportement en fonction du loop
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player?.currentItem,
            queue = null
        ) { notification ->
            if (loop) {
                player?.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
                player?.play()
            } else {
                // Si le loop est désactivé, on met en pause pour arrêter la lecture
                player?.pause()
                _isPlaying = false
            }
        }

        // Démarrer l'observateur de position et marquer la présence d'un média
        startPositionUpdates()
        _hasMedia = true

        // Lancer la lecture automatiquement
        play()
    }

    /**
     * Démarre la lecture.
     */
    actual fun play() {
        println("[VideoPlayerState] play appelé")
        if (player == null) {
            println("[VideoPlayerState] play: player est null")
            return
        }
        player?.volume = volume
        player?.play()
        _isPlaying = true
    }

    /**
     * Met en pause la lecture.
     */
    actual fun pause() {
        println("[VideoPlayerState] pause appelé")
        player?.pause()
        _isPlaying = false
    }

    /**
     * Arrête la lecture et revient au début.
     */
    actual fun stop() {
        println("[VideoPlayerState] stop appelé")
        player?.pause()
        player?.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
        _isPlaying = false
    }

    /**
     * Recherche la position cible à partir d'une valeur entre 0 et 1000, puis déplace la lecture.
     */
    actual fun seekTo(value: Float) {
        if (_duration > 0) {
            val targetTime = _duration * (value / 1000.0)
            player?.seekToTime(CMTimeMakeWithSeconds(targetTime, 1))
        }
    }

    /**
     * Cache le média.
     */
    actual fun hideMedia() {
        println("[VideoPlayerState] hideMedia appelé")
        _hasMedia = false
    }

    /**
     * Affiche le média.
     */
    actual fun showMedia() {
        println("[VideoPlayerState] showMedia appelé")
        _hasMedia = true
    }

    /**
     * Efface l'erreur (non implémenté dans cette version).
     */
    actual fun clearError() {
        println("[VideoPlayerState] clearError appelé")
    }

    /**
     * Libère les ressources et arrête tous les observateurs.
     */
    actual fun dispose() {
        println("[VideoPlayerState] dispose appelé")
        stopPositionUpdates()
        removeEndObserver()
        player?.pause()
        player = null
        _hasMedia = false
        _isPlaying = false
    }

    /**
     * Ouvre un fichier vidéo.
     * La conversion du fichier en URI se fait via toString().
     */
    actual fun openFile(file: PlatformFile) {
        println("[VideoPlayerState] openFile appelé avec file: $file")
        openUri(file.toString())
    }

    // Propriétés et méthodes non implémentées
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
