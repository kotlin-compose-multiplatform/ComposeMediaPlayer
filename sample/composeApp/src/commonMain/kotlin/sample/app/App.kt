package sample.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType

@Composable
fun App() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        var url by remember {
            mutableStateOf("http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
        }

        MaterialTheme {
            val playerState = rememberVideoPlayerState()
            val snackbarHostState = remember { SnackbarHostState() }

            val fileKitLauncher = rememberFilePickerLauncher(
                type = PickerType.Video,
                title = "Select a Video File",
                onResult = { file ->
                    file?.path?.let { playerState.openUri(it) }
                    print(file?.path)
                }
            )

            // Gestion des erreurs via LaunchedEffect
            LaunchedEffect(playerState.error) {
                playerState.error?.let { error ->
                    val message = when (error) {
                        is VideoPlayerError.CodecError -> "Codec Error: ${error.message}"
                        is VideoPlayerError.NetworkError -> "Network Error: ${error.message}"
                        is VideoPlayerError.SourceError -> "Source Error: ${error.message}"
                        is VideoPlayerError.UnknownError -> "Unknown Error: ${error.message}"
                    }
                    snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = "Clear",
                        duration = SnackbarDuration.Indefinite
                    ).let {
                        if (it == SnackbarResult.ActionPerformed) {
                            playerState.clearError()
                        }
                    }
                }
            }

            Scaffold(
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState)
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(8.dp)
                ) {
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
                        IconButton(onClick = { fileKitLauncher.launch() }) {
                            Icon(
                                imageVector = Icons.Default.UploadFile,
                                contentDescription = "Pick a local file"
                            )
                        }

                        IconButton(
                            onClick = {
                                if (playerState.isPlaying) playerState.pause()
                                else playerState.play()
                            }
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying)
                                    Icons.Default.Pause
                                else
                                    Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play"
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Loop?")
                            Switch(
                                checked = playerState.loop,
                                onCheckedChange = { playerState.loop = it }
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Volume: ${(playerState.volume * 100).toInt()}%")
                            Slider(
                                value = playerState.volume,
                                onValueChange = { playerState.volume = it },
                                valueRange = 0f..1f,
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // URL input
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

                    // Timeline
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

                    Text("Time: ${playerState.positionText} / ${playerState.durationText}")
                    Text("L: ${playerState.leftLevel.toInt()}%  R: ${playerState.rightLevel.toInt()}%")
                }
            }
        }
    }
}