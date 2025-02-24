@file:OptIn(ExperimentalForeignApi::class)
package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import io.github.kdroidfilter.composemediaplayer.util.formatTime
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.NSURL
import platform.darwin.dispatch_get_main_queue

@Stable
actual open class VideoPlayerState {
    // États basiques
    actual var volume: Float = 1.0f
    actual var sliderPos: Float = 0.0f      // Valeur comprise entre 0 et 1 représentant la progression
    actual var userDragging: Boolean = false
    actual var loop: Boolean = false

    // États de lecture
    actual val hasMedia: Boolean get() = _hasMedia
    actual val isPlaying: Boolean get() = _isPlaying
    actual val leftLevel: Float = 0.0f
    actual val rightLevel: Float = 0.0f

    // Les textes de temps sont exposés en tant que val via des variables d'état privées
    private var _positionText: String by mutableStateOf("00:00")
    actual val positionText: String get() = _positionText

    private var _durationText: String by mutableStateOf("00:00")
    actual val durationText: String get() = _durationText

    actual val isLoading: Boolean = false
    actual val error: VideoPlayerError? = null

    private var _hasMedia = false
    private var _isPlaying = false

    // Instance observable du player pour Compose
    var player: AVPlayer? by mutableStateOf(null)
        private set

    // Token pour l'observateur périodique
    private var timeObserverToken: Any? = null

    // Métadonnées et sous-titres (non entièrement implémentés ici)
    actual val metadata: VideoMetadata = VideoMetadata()
    actual var subtitlesEnabled: Boolean = false
    actual var currentSubtitleTrack: SubtitleTrack? = null
    actual val availableSubtitleTracks: MutableList<SubtitleTrack> = mutableListOf()

    actual fun openUri(uri: String) {
        println("[VideoPlayerState] openUri appelé avec uri: $uri")
        val nsUrl = NSURL.URLWithString(uri) ?: run {
            println("[VideoPlayerState] Échec de la création d'NSURL depuis uri: $uri")
            return
        }

        // Nettoyage de l'observateur précédent (s'il existe)
        removeTimeObserver()

        // Pause et remplacement du player existant
        player?.pause()

        // Création du player avec un AVPlayerItem
        val playerItem = AVPlayerItem(uRL = nsUrl)
        player = AVPlayer(playerItem = playerItem).apply {
            volume = this@VideoPlayerState.volume
        }

        // Ajout de l'observateur pour la progression
        addTimeObserver()

        _hasMedia = true
        println("[VideoPlayerState] AVPlayer créé avec succès pour uri: $uri")
    }

    actual fun openFile(file: PlatformFile) {
        println("[VideoPlayerState] openFile appelé avec file: $file")
        openUri(file.toString())
    }

    actual fun play() {
        println("[VideoPlayerState] play appelé")
        if (player == null) {
            println("[VideoPlayerState] play: player est null")
        } else {
            player?.play()
            println("[VideoPlayerState] play: lecture démarrée")
        }
        _isPlaying = true
    }

    actual fun pause() {
        println("[VideoPlayerState] pause appelé")
        if (player == null) {
            println("[VideoPlayerState] pause: player est null")
        } else {
            player?.pause()
            println("[VideoPlayerState] pause: lecture en pause")
        }
        _isPlaying = false
    }

    actual fun stop() {
        println("[VideoPlayerState] stop appelé")
        if (player == null) {
            println("[VideoPlayerState] stop: player est null")
        } else {
            player?.pause()
            player?.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
            println("[VideoPlayerState] stop: lecture arrêtée et remise au début")
        }
        _isPlaying = false
    }

    /**
     * Déplace la lecture à la position donnée (en secondes).
     */
    actual fun seekTo(value: Float) {
        println("[VideoPlayerState] seekTo appelé avec value: $value")
        player?.seekToTime(CMTimeMakeWithSeconds(value.toDouble(), 1))
    }

    actual fun hideMedia() {
        println("[VideoPlayerState] hideMedia appelé")
        _hasMedia = false
    }

    actual fun showMedia() {
        println("[VideoPlayerState] showMedia appelé")
        _hasMedia = true
    }

    actual fun dispose() {
        println("[VideoPlayerState] dispose appelé")
        if (player == null) {
            println("[VideoPlayerState] dispose: player est null")
        } else {
            removeTimeObserver()
            player?.pause()
            println("[VideoPlayerState] dispose: player en pause et libéré")
        }
        player = null
        _hasMedia = false
        _isPlaying = false
    }

    actual fun clearError() {
        println("[VideoPlayerState] clearError appelé")
        // Pas de gestion d'erreur dans cette version basique
    }

    /**
     * Retourne l'instance du player (utilisé dans la vue Compose).
     */
    fun getPlayer(): AVPlayer? {
        println("[VideoPlayerState] getPlayer appelé, renvoi: $player")
        return player
    }

    actual fun selectSubtitleTrack(track: SubtitleTrack?) {
        currentSubtitleTrack = track
        println("[VideoPlayerState] Piste de sous-titres sélectionnée: $track")
    }

    actual fun disableSubtitles() {
        subtitlesEnabled = false
        println("[VideoPlayerState] Sous-titres désactivés")
    }

    // --- Implémentation de l'observateur de temps ---
    /**
     * Ajoute un observateur périodique pour mettre à jour la position, la durée et le slider.
     */
    private fun addTimeObserver() {
        val interval = CMTimeMakeWithSeconds(1.0, 1) // intervalle de 1 seconde
        timeObserverToken = player?.addPeriodicTimeObserverForInterval(
            interval = interval,
            queue = dispatch_get_main_queue(),
            usingBlock = { time ->
                // Temps actuel et durée totale en secondes
                val currentSeconds = CMTimeGetSeconds(time)
                val durationSeconds = player?.currentItem?.duration?.let { CMTimeGetSeconds(it) } ?: 0.0

                // Mise à jour du slider (progression)
                sliderPos = if (durationSeconds > 0) (currentSeconds / durationSeconds).toFloat() else 0.0f

                // Mise à jour des textes affichant le temps via les variables internes
                _positionText = formatTime(currentSeconds.toFloat())
                _durationText = formatTime(durationSeconds.toFloat())

                // Mode boucle : redémarrage si la fin est atteinte
                if (loop && durationSeconds > 0 && currentSeconds >= durationSeconds) {
                    player?.seekToTime(CMTimeMakeWithSeconds(0.0, 1))
                    player?.play()
                }
            }
        )
    }

    /**
     * Supprime l'observateur de temps s'il est actif.
     */
    private fun removeTimeObserver() {
        timeObserverToken?.let { token ->
            player?.removeTimeObserver(token)
            timeObserverToken = null
        }
    }


}
