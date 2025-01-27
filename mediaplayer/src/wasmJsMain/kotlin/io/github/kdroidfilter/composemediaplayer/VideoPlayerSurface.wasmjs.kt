package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import kotlin.math.abs

@Composable
actual fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier) {
    var videoElement by remember { mutableStateOf<HTMLVideoElement?>(null) }
    val scope = rememberCoroutineScope()

    // Créer l'élément vidéo HTML
    HtmlView(
        factory = {
            createVideoElement()
        },
        modifier = modifier,
        update = { video ->
            videoElement = video
            setupVideoElement(video, playerState, scope)
        }
    )

    // Gérer les effets de la source URI
    LaunchedEffect(playerState.sourceUri) {
        videoElement?.let {
            it.src = playerState.sourceUri ?: ""
            if (playerState.isPlaying) {
                it.play()
            } else {
                it.pause()
            }
        }
    }

    // Gérer les effets de la lecture
    LaunchedEffect(playerState.isPlaying) {
        videoElement?.let {
            if (playerState.isPlaying) {
                it.play()
            } else {
                it.pause()
            }
        }
    }

    // Gérer les effets du volume
    LaunchedEffect(playerState.volume) {
        videoElement?.volume = playerState.volume.toDouble()
    }

    // Gérer les effets du loop
    LaunchedEffect(playerState.loop) {
        videoElement?.loop = playerState.loop
    }

    // Gérer les effets du seek via sliderPos avec debounce
    LaunchedEffect(playerState.sliderPos) {
        if (!playerState.userDragging && playerState.hasMedia) {
            val job = scope.launch {
                val duration = videoElement?.duration?.toFloat() ?: 0f
                if (duration > 0f) {
                    val newTime = (playerState.sliderPos / VideoPlayerState.PERCENTAGE_MULTIPLIER) * duration
                    // Éviter de mettre à jour si la différence est trop petite
                    if (abs((videoElement?.currentTime ?: 0.0) - newTime) > 0.5) {
                        videoElement?.currentTime = newTime.toDouble()
                    }
                }
            }
            // Annuler le précédent job si un nouveau sliderPos est reçu avant le délai
            playerState.seekJob?.cancel()
            playerState.seekJob = job
        }
    }
}

private fun createVideoElement(): HTMLVideoElement {
    return (document.createElement("video") as HTMLVideoElement).apply {
        controls = false
        style.width = "100%"
        style.height = "100%"
    }
}

private fun setupVideoElement(
    video: HTMLVideoElement,
    playerState: VideoPlayerState,
    scope: CoroutineScope
) {
    // Nettoyer les anciens écouteurs
    video.removeEventListener("timeupdate", playerState::onTimeUpdateEvent)
    video.addEventListener("timeupdate", playerState::onTimeUpdateEvent)

    // Événement déclenché quand la vidéo commence une opération de seek
    video.addEventListener("seeking", {
        scope.launch {
            playerState._isLoading = true
        }
    })

    // Événement déclenché quand l'opération de seek est terminée
    video.addEventListener("seeked", {
        scope.launch {
            playerState._isLoading = false
        }
    })

    // Événement déclenché quand la vidéo attend des données (buffering)
    video.addEventListener("waiting", {
        scope.launch {
            playerState._isLoading = true
        }
    })

    // Événement déclenché quand la vidéo peut commencer ou reprendre la lecture
    video.addEventListener("playing", {
        scope.launch {
            playerState._isLoading = false
        }
    })

    // Événement déclenché quand la vidéo peut être lue jusqu'au bout sans interruption
    video.addEventListener("canplaythrough", {
        scope.launch {
            playerState._isLoading = false
        }
    })

    // Événement pour l'état "can play"
    video.addEventListener("canplay", {
        scope.launch {
            playerState._isLoading = false
        }
    })

    video.addEventListener("suspend", {
        scope.launch {
            // Ne mettre isLoading à false que si nous avons assez de données pour jouer
            if (video.readyState >= 3) { // HAVE_FUTURE_DATA ou mieux
                playerState._isLoading = false
            }
        }
    })

    // Écouteur pour les erreurs de lecture
    video.addEventListener("error", {
        scope.launch {
            playerState._isLoading = false
        }
    })

    // Écouteur pour les métadonnées
    video.addEventListener("loadedmetadata", {
        scope.launch {
            playerState._isLoading = false
            if (playerState.isPlaying) {
                try {
                    video.play()
                } catch (e: Exception) {
                    println("Error opening media: ${e.message}")
                }
            }
        }
    })

    // Appliquer volume, loop, etc.
    video.volume = playerState.volume.toDouble()
    video.loop = playerState.loop

    // Lecture si la source est définie et l'état l'autorise
    if (video.src.isNotEmpty() && playerState.isPlaying) {
        try {
            video.play()
        } catch (e: Exception) {
            println("Error opening media: ${e.message}")
        }
    }
}

// Ajouter une extension pour gérer l'événement "timeupdate"
private fun VideoPlayerState.onTimeUpdateEvent(event: Event) {
    val video = event.target as? HTMLVideoElement
    video?.let {
        onTimeUpdate(it.currentTime.toFloat(), it.duration.toFloat())
    }
}
