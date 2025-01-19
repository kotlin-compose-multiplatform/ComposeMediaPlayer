
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import javafx.application.Platform
import java.time.Duration
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun App() {
    MaterialTheme {
        VideoPlayerApp()
    }
}

@Composable
fun VideoPlayerApp() {
    val playerState = remember { WindowsVideoPlayerState() }
    val mediaState by playerState.mediaState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Zone de la vidéo
        WindowsVideoPlayerSurface(
            playerState = playerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        // Contrôles de lecture
        PlayerControls(
            mediaState = mediaState,
            onPlayPause = playerState::togglePlayPause,
            onStop = playerState::stopMedia,
            onSeek = playerState::seekToPercent,
            onOpenFile = {
                val fileChooser = JFileChooser().apply {
                    fileFilter = FileNameExtensionFilter(
                        "Fichiers vidéo", "mp4", "mkv", "avi"
                    )
                }
                if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    playerState.openMedia(fileChooser.selectedFile.absolutePath)
                }
            }
        )
    }
}

@Composable
fun PlayerControls(
    mediaState: MediaState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onSeek: (Double) -> Unit,
    onOpenFile: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        // Barre de progression
        Slider(
            value = if (mediaState.duration > 0)
                (mediaState.currentTime / mediaState.duration).toFloat()
            else 0f,
            onValueChange = { onSeek(it * 100.0) },
            enabled = mediaState.hasMedia,
            modifier = Modifier.fillMaxWidth()
        )

        // Affichage du temps
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(mediaState.currentTime))
            Text(formatTime(mediaState.duration))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Boutons de contrôle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onOpenFile) {
                Text("Ouvrir un fichier")
            }

            Button(
                onClick = onPlayPause,
                enabled = mediaState.hasMedia
            ) {
                Text(if (mediaState.isPlaying) "Pause" else "Lecture")
            }

            Button(
                onClick = onStop,
                enabled = mediaState.hasMedia
            ) {
                Text("Stop")
            }
        }
    }
}

private fun formatTime(seconds: Double): String {
    val duration = Duration.ofSeconds(seconds.toLong())
    val hours = duration.toHours()
    val minutes = duration.toMinutesPart()
    val secs = duration.toSecondsPart()

    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%02d:%02d", minutes, secs)
    }
}

fun main() = application {

    val windowState = rememberWindowState(
        width = 1024.dp,
        height = 768.dp
    )

    Window(
        onCloseRequest = {
            Platform.exit()
            exitApplication()
        },
        title = "Compose Player JavaFX",
        state = windowState
    ) {
        App()
    }
}