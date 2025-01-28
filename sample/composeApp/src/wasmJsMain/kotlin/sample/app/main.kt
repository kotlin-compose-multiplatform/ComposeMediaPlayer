import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import io.github.kdroidfilter.composemediaplayer.htmlinterop.LocalLayerContainer
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

