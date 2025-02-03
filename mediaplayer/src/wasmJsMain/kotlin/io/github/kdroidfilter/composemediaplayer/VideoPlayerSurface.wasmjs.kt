package io.github.kdroidfilter.composemediaplayer

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.kdroidfilter.composemediaplayer.htmlinterop.HtmlView
import io.github.kdroidfilter.composemediaplayer.util.logger
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.w3c.dom.DISABLED
import org.w3c.dom.HTMLTrackElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.SHOWING
import org.w3c.dom.TextTrackMode
import org.w3c.dom.events.Event
import kotlin.math.abs

@Composable
actual fun VideoPlayerSurface(playerState: VideoPlayerState, modifier: Modifier) {
    var videoElement by remember { mutableStateOf<HTMLVideoElement?>(null) }
    val scope = rememberCoroutineScope()

    // On stocke la liste des <track> créés pour pouvoir éventuellement les mettre à jour
    // ou les supprimer si besoin.
    val trackElements = remember { mutableStateMapOf<String, HTMLTrackElement>() }

    // Create HTML video element
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

    /**
     * Gestion de la source
     */
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

    /**
     * Play/Pause
     */
    LaunchedEffect(playerState.isPlaying) {
        videoElement?.let {
            if (playerState.isPlaying) {
                it.play()
            } else {
                it.pause()
            }
        }
    }

    /**
     * Volume
     */
    LaunchedEffect(playerState.volume) {
        videoElement?.volume = playerState.volume.toDouble()
    }

    /**
     * Loop
     */
    LaunchedEffect(playerState.loop) {
        videoElement?.loop = playerState.loop
    }

    /**
     * Seek (sliderPos) avec debounce
     */
    LaunchedEffect(playerState.sliderPos) {
        if (!playerState.userDragging && playerState.hasMedia) {
            val job = scope.launch {
                val duration = videoElement?.duration?.toFloat() ?: 0f
                if (duration > 0f) {
                    val newTime = (playerState.sliderPos / VideoPlayerState.PERCENTAGE_MULTIPLIER) * duration
                    // On évite un seek si la différence est trop minime
                    if (abs((videoElement?.currentTime ?: 0.0) - newTime) > 0.5) {
                        videoElement?.currentTime = newTime.toDouble()
                    }
                }
            }
            playerState.seekJob?.cancel()
            playerState.seekJob = job
        }
    }

    /**
     * Gestion dynamique des sous-titres :
     * - On observe la liste des pistes disponibles.
     * - On crée/supprime les <track> en conséquence.
     * - On active/désactive la piste choisie par l’utilisateur.
     */
    LaunchedEffect(playerState.availableSubtitleTracks) {
        videoElement?.let { video ->

            // 1) Supprimer les pistes qui ne sont plus dans la liste
            val currentIds = playerState.availableSubtitleTracks.map { it.id }.toSet()
            val toRemove = trackElements.keys.filter { it !in currentIds }
            toRemove.forEach { removedId ->
                trackElements[removedId]?.let { trackNode ->
                    video.removeChild(trackNode)
                }
                trackElements.remove(removedId)
            }

            // 2) Ajouter les nouvelles pistes qui n’existent pas encore
            playerState.availableSubtitleTracks.forEach { track ->
                if (!trackElements.containsKey(track.id)) {
                    val newTrackElement = (document.createElement("track") as HTMLTrackElement).apply {
                        kind = "subtitles"
                        srclang = track.language
                        label = track.name
                        // La source du fichier de sous-titres (WebVTT de préférence)
                        src = track.src
                        // mode = "disabled" par défaut, ou "hidden"
                        // "showing" si on veut qu’ils soient actifs tout de suite
                        default = false
                    }
                    video.appendChild(newTrackElement)
                    trackElements[track.id] = newTrackElement
                }
            }

            // 3) Mettre à jour la piste sélectionnée
            refreshSubtitleSelection(video, trackElements, playerState)
        }
    }

    /**
     * Sur le changement de la piste actuelle ou si user désactive les sous-titres,
     * on appelle une fonction pour ajuster le mode.
     */
    LaunchedEffect(playerState.currentSubtitleTrack, playerState.subtitlesEnabled) {
        videoElement?.let { video ->
            refreshSubtitleSelection(video, trackElements, playerState)
        }
    }
}

/**
 * Ajuste l'état de chaque <track> pour qu'il corresponde à la piste choisie.
 */
private fun refreshSubtitleSelection(
    video: HTMLVideoElement,
    trackElements: Map<String, HTMLTrackElement>,
    playerState: VideoPlayerState
) {
    // Si les sous-titres sont désactivés, on met tout en "disabled" ou "hidden"
    if (!playerState.subtitlesEnabled || playerState.currentSubtitleTrack == null) {
        trackElements.values.forEach { track ->
            track.track.mode = TextTrackMode.SHOWING
            track.default = false
        }
        return
    }

    // Sinon, on active uniquement la piste correspondant à currentSubtitleTrack
    val selectedId = playerState.currentSubtitleTrack?.id
    trackElements.forEach { (id, track) ->
        if (id == selectedId) {
            track.track.mode = TextTrackMode.SHOWING
            track.default = true

        } else {
            track.track.mode = TextTrackMode.DISABLED
            track.default = false

        }
    }
}

private fun createVideoElement(): HTMLVideoElement {
    return (document.createElement("video") as HTMLVideoElement).apply {
        controls = false
        style.width = "100%"
        style.height = "100%"
        crossOrigin = "anonymous"
    }
}

/**
 * Configure l’élément vidéo : event listeners, WebAudioAnalyzer, etc.
 */
fun setupVideoElement(
    video: HTMLVideoElement,
    playerState: VideoPlayerState,
    scope: CoroutineScope,
    enableAudioDetection: Boolean = true
) {
    logger.debug { "Setup video => enableAudioDetection = $enableAudioDetection" }

    val audioAnalyzer = if (enableAudioDetection) {
        AudioLevelProcessor(video)
    } else null

    var initializationJob: Job? = null

    fun initAudioAnalyzer() {
        if (!enableAudioDetection) return
        initializationJob?.cancel()
        initializationJob = scope.launch {
            audioAnalyzer?.initialize()
        }
    }

    video.addEventListener("loadedmetadata") {
        logger.debug { "Video => loadedmetadata => init analyzer if enabled" }
        initAudioAnalyzer()
    }

    video.addEventListener("play") {
        logger.debug { "Video => play => init analyzer if needed" }
        if (enableAudioDetection && initializationJob?.isActive != true) {
            initAudioAnalyzer()
        }
        if (enableAudioDetection) {
            scope.launch {
                logger.debug { "Starting audio level update loop" }
                while (true) {
                    val (left, right) = audioAnalyzer?.getAudioLevels() ?: (0f to 0f)
                    playerState.updateAudioLevels(left, right)
                    delay(100)
                }
            }
        }
    }

    // timeupdate
    video.removeEventListener("timeupdate", playerState::onTimeUpdateEvent)
    video.addEventListener("timeupdate", playerState::onTimeUpdateEvent)

    // seeking, waiting, canplay, etc.
    video.addEventListener("seeking") {
        scope.launch { playerState._isLoading = true }
    }
    video.addEventListener("seeked") {
        scope.launch { playerState._isLoading = false }
    }
    video.addEventListener("waiting") {
        scope.launch { playerState._isLoading = true }
    }
    video.addEventListener("playing") {
        scope.launch { playerState._isLoading = false }
    }
    video.addEventListener("canplaythrough") {
        scope.launch { playerState._isLoading = false }
    }
    video.addEventListener("canplay") {
        scope.launch { playerState._isLoading = false }
    }
    video.addEventListener("suspend") {
        scope.launch {
            if (video.readyState >= 3) {
                playerState._isLoading = false
            }
        }
    }
    // error
    video.addEventListener("error") {
        scope.launch {
            playerState._isLoading = false
            logger.error { "Video => error => possibly no audio analyzer if CORS issues." }
        }
    }

    video.addEventListener("loadedmetadata") {
        scope.launch {
            playerState._isLoading = false
            if (playerState.isPlaying) {
                try {
                    video.play()
                } catch (e: Exception) {
                    logger.error(e) { "Error opening media: ${e.message}" }
                }
            }
        }
    }

    // Volume, loop init
    video.volume = playerState.volume.toDouble()
    video.loop = playerState.loop

    // Si la source est déjà présente et qu'on veut lire
    if (video.src.isNotEmpty() && playerState.isPlaying) {
        try {
            video.play()
        } catch (e: Exception) {
            logger.error(e) { "Error opening media: ${e.message}" }
        }
    }
}

// Gère l'event timeupdate
private fun VideoPlayerState.onTimeUpdateEvent(event: Event) {
    val video = event.target as? HTMLVideoElement
    video?.let {
        onTimeUpdate(it.currentTime.toFloat(), it.duration.toFloat())
    }
}
