@file:OptIn(ExperimentalForeignApi::class)
package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.composemediaplayer.util.formatTime
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.AVFoundation.*
import platform.CoreGraphics.CGFloat
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue

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

    // Mise à jour du isLoading
    private var _isLoading by mutableStateOf(false)
    actual val isLoading: Boolean
        get() = _isLoading

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

    // Rapport d'aspect vidéo observable (initialisé par défaut à 16:9)
    private var _videoAspectRatio by mutableStateOf(16.0 / 9.0)
    val videoAspectRatio: CGFloat
        get() = _videoAspectRatio

    /**
     * Démarre un observateur périodique pour mettre à jour la progression, l'aspect ratio
     * et l'état de buffering (isLoading).
     */
    private fun startPositionUpdates() {
        stopPositionUpdates() // Nettoyage d'un observateur existant
        val interval = CMTimeMakeWithSeconds(1.0 / 60.0, 600) // environ 60 fps
        timeObserverToken = player?.addPeriodicTimeObserverForInterval(
            interval = interval,
            queue = dispatch_get_main_queue(),
            usingBlock = { time ->
                // Mise à jour du temps et de la position
                val currentSeconds = CMTimeGetSeconds(time)
                val durationSeconds = player?.currentItem?.duration?.let { CMTimeGetSeconds(it) } ?: 0.0
                _currentTime = currentSeconds
                _duration = durationSeconds

                if (!userDragging && durationSeconds > 0) {
                    sliderPos = ((currentSeconds / durationSeconds) * 1000).toFloat()
                }
                _positionText = formatTime(currentSeconds.toFloat())
                _durationText = formatTime(durationSeconds.toFloat())

                // Mise à jour de l'aspect ratio en fonction de la presentationSize, si disponible
                player?.currentItem?.presentationSize?.useContents {
                    val newAspect = if (height != 0.0) width / height else 16.0 / 9.0
                    if (newAspect != _videoAspectRatio) {
                        _videoAspectRatio = newAspect
                    }
                }

                // Vérification de l'état de buffering : si le tampon est vide ou si la lecture n'est pas jugée fluide,
                // alors on considère que la vidéo est en chargement.
                player?.currentItem?.let { item ->
                    val isBufferEmpty = item.playbackBufferEmpty
                    val isLikelyToKeepUp = item.playbackLikelyToKeepUp
                    _isLoading = isBufferEmpty || !isLikelyToKeepUp
                } ?: run {
                    _isLoading = false
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

        // Création d'un nouvel AVPlayerItem
        val playerItem = AVPlayerItem(nsUrl)

        // Mise à jour initiale du rapport d'aspect (il sera aussi mis à jour dans l'observateur périodique)
        playerItem.presentationSize.useContents {
            _videoAspectRatio = if (height != 0.0) width / height else 16.0 / 9.0
        }

        // Création du player et configuration initiale
        player = AVPlayer(playerItem = playerItem).apply {
            volume = this@VideoPlayerState.volume
            // Désactivation de l'action automatique à la fin pour gérer le loop nous-mêmes
            actionAtItemEnd = AVPlayerActionAtItemEndNone
        }

        // Ajout de l'observateur pour la fin du média
        endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player?.currentItem,
            queue = null
        ) { notification ->
            if (loop) {
                player?.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
                player?.play()
            } else {
                player?.pause()
                _isPlaying = false
            }
        }

        // Démarrage de l'observateur de position et marquage du média chargé
        startPositionUpdates()
        _hasMedia = true

        // Lancement de la lecture automatiquement
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
        // Lors du lancement de la lecture, on considère que le buffering est terminé
        _isLoading = false
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

