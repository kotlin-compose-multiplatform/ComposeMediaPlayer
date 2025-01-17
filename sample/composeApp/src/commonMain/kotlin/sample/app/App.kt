package sample.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.vinceglb.filekit.compose.PickerResultLauncher
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType
import io.github.vinceglb.filekit.core.baseName

@Composable
fun App() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        var url by remember { mutableStateOf("https://download.blender.org/peach/bigbuckbunny_movies/BigBuckBunny_320x180.mp4") }

        MaterialTheme {
            val playerState = rememberVideoPlayerState()

            val fileKitLauncher = rememberFilePickerLauncher(
                type = PickerType.Video,
                title = "Select a Video File",
                onResult = { file ->
                    file?.path?.let { playerState.openUri(it) }
                    print(file?.path)
                },
            )

            Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {

                // Video zone
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    VideoPlayerSurface(
                        playerState = playerState,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // ---- Replaces the old pickFileDialog button ----
                    Button(onClick = { fileKitLauncher.launch() }) {
                        Text("File...")
                    }

                    Button(onClick = { playerState.play() }) {
                        Text("Play")
                    }

                    Button(onClick = { playerState.pause() }) {
                        Text("Pause")
                    }

                    Text("Loop?")
                    Switch(
                        checked = playerState.loop,
                        onCheckedChange = { playerState.loop = it }
                    )

                    Text("Volume: ${(playerState.volume * 100).toInt()}%")
                    Slider(
                        value = playerState.volume,
                        onValueChange = { playerState.volume = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.width(120.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Open from a URL
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (url.isNotEmpty()) {
                                playerState.openUri(url)
                            }
                        },
                        modifier = Modifier.weight(0.2f)
                    ) {
                        Text("Open URL")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Timeline slider
                Text("Position:")
                Slider(
                    value = playerState.sliderPos,
                    onValueChange = {
                        playerState.sliderPos = it
                        playerState.userDragging = true
                    },
                    onValueChangeFinished = {
                        playerState.userDragging = false
                        playerState.seekTo(playerState.sliderPos)
                    },
                    valueRange = 0f..1000f
                )

                // Display current time / total duration
                Text("Time: ${playerState.positionText} / ${playerState.durationText}")

                // Left/Right audio levels
                Text("L: ${playerState.leftLevel.toInt()}%  R: ${playerState.rightLevel.toInt()}%")
            }
        }
    }
}
