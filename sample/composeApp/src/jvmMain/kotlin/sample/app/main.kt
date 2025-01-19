package sample.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.kdroidfilter.composemediaplayer.linux.GStreamerInit

fun main() = application {

    val windowState = rememberWindowState()

    Window(
        onCloseRequest = ::exitApplication,
        title = "GStreamer + Compose Player",
        state = windowState
    ) {
        GStreamerInit.setGStreamerPathWindows("E:\\gstreamer\\1.0\\msvc_x86_64\\bin")
        App()

    }
}


