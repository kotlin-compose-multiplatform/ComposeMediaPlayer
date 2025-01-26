import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import androidx.compose.ui.window.ComposeViewport
import io.github.kdroidfilter.composemediaplayer.LocalLayerContainer
import kotlinx.browser.document
import sample.app.App

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(canvasElementId = "ComposeTarget", title = "Compose Media Player") {
        CompositionLocalProvider(LocalLayerContainer provides document.body!!) {
            App()
        }
    }
}

