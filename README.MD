# 🎥 Compose Media Player

**Compose Media Player** is a video player library designed for Jetpack Compose, supporting multiple platforms including Android, macOS, Windows, and Linux. It is the first fully functional multiplatform video player for Compose that requires no additional software installations. The library leverages:

- **GStreamer** for Linux
- **JavaFX** for macOS and Windows (using native platform APIs)
- **Media3** for Android

## ✨ Features

- **Multiplatform Support**: Works seamlessly on Android, macOS, Windows, and Linux.
- **File and URL Support**: Play videos from local files or directly from URLs.
- **Media Controls**: Includes play, pause, loop toggle, volume control, and timeline slider.
- **Custom Video Player UI**: Fully customizable using Jetpack Compose.
- **Audio Levels**: Displays left and right audio levels in real time.

## 🔧 Installation

To add Compose Media Player to your project, include the following dependency in your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("io.github.kdroidfilter:composemediaplayer:<latest_version>")
}
```

Replace `<latest_version>` with the latest version available on [Maven Central](https://search.maven.org/).

## 🚀 Getting Started

### 📋 Basic Example

Here is a minimal example of how to integrate the Compose Media Player into your Compose application with a hardcoded URL:

```kotlin
@Composable
fun App() {
    val playerState = rememberVideoPlayerState()

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {

            // Video Surface
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

            // Playback Controls
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { playerState.play() }) { Text("Play") }
                Button(onClick = { playerState.pause() }) { Text("Pause") }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Open Video URL
            Button(
                onClick = {
                    val url = "http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                    playerState.openUri(url)
                }
            ) {
                Text("Open Video")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Volume Control
            Text("Volume: ${(playerState.volume * 100).toInt()}%")
            Slider(
                value = playerState.volume,
                onValueChange = { playerState.volume = it },
                valueRange = 0f..1f
            )
        }
    }
}
```

See the example above for more details.

## 📄 License

Compose Media Player is licensed under the MIT License. See [LICENSE](LICENSE) for details.

---

For contributions, issues, or feature requests, visit the [GitHub repository](https://github.com/your-repo/compose-media-player).

