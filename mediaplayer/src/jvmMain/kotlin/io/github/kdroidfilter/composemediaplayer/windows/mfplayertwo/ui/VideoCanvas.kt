package io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.ui

import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.util.Logger
import java.awt.Canvas
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Canvas dedicated to video display.
 * UpdateVideo() is called only when the Canvas is resized.
 */
class VideoCanvas : Canvas() {
    private val logger = Logger("VideoCanvas")
    var onResize: (() -> Unit)? = null  // Callback to handle resize events

    init {
        isVisible = true
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                logger.log("Canvas resized to: ${width}x${height}")
                onResize?.invoke()
            }
        })
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        // Si vous avez besoin de redessiner quelque chose lors du paint, vous pouvez le faire ici
    }
}
