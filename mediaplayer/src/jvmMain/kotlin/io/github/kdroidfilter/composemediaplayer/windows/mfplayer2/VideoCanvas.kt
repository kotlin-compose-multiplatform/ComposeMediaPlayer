package io.github.kdroidfilter.composemediaplayer.windows.mfplayer2

import java.awt.Canvas
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Canvas dedicated to video display.
 * Here, UpdateVideo() is no longer called systematically in paint().
 */
class VideoCanvas : Canvas() {
    private val logger2 = Logger2("VideoCanvas")

    init {
        background = Color.BLACK
        preferredSize = Dimension(1280, 720)
        isVisible = true
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                logger2.log("Canvas resized to: ${width}x${height}")
            }
        })
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        if (!MediaPlayerLib2.INSTANCE.HasVideo()) {
            // Draw black if no video
            g.color = Color.BLACK
            g.fillRect(0, 0, width, height)
        }
    }
}