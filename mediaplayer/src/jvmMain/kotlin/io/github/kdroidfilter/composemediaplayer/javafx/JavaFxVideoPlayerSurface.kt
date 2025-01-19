package io.github.kdroidfilter.composemediaplayer.javafx
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.MediaView

/**
 * Composable function for rendering a JavaFX-based video player surface.
 * Utilizes a SwingPanel to embed JavaFX content within a Compose application.
 *
 * @param playerState An instance of JavaFxVideoPlayerState used to manage the state
 *                    and functionality of the video player.
 * @param modifier    A Modifier to specify layout and styling for the video player container.
 */
@Composable
fun JavaFxVideoPlayerSurface(
    playerState: JavaFxVideoPlayerState,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val jfxPanel = JFXPanel()

                Platform.runLater {
                    val stackPane = StackPane()
                    val scene = Scene(stackPane)

                    val mediaView = MediaView().apply {
                        mediaPlayer = playerState.mediaPlayer
                        isPreserveRatio = true
                    }

                    playerState.updateMediaView(mediaView)

                    // Configuration du redimensionnement
                    stackPane.children.add(mediaView)
                    StackPane.setAlignment(mediaView, Pos.CENTER)

                    // Liaison bidirectionnelle pour le redimensionnement
                    stackPane.widthProperty().addListener { _, _, newWidth ->
                        mediaView.fitWidth = newWidth.toDouble()
                    }
                    stackPane.heightProperty().addListener { _, _, newHeight ->
                        mediaView.fitHeight = newHeight.toDouble()
                    }

                    // Gestion du redimensionnement de la fenêtre
                    scene.windowProperty().addListener { _, _, newWindow ->
                        if (newWindow != null) {
                            newWindow.widthProperty().addListener { _, _, _ ->
                                mediaView.fitWidth = stackPane.width
                            }
                            newWindow.heightProperty().addListener { _, _, _ ->
                                mediaView.fitHeight = stackPane.height
                            }
                        }
                    }

                    jfxPanel.scene = scene
                }
                jfxPanel
            }
        )
    }
}