package sample.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerMode
import io.github.vinceglb.filekit.core.PickerType

fun main() = application {

    val windowState = rememberWindowState()

    Window(
        onCloseRequest = ::exitApplication,
        title = "GStreamer + Compose Player",
        state = windowState
    ) {
        App()
    }
}


