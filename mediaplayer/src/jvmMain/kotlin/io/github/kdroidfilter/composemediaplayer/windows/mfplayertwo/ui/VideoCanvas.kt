package io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.ui

import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.util.Logger
import java.awt.Canvas
import java.awt.Graphics
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

/**
 * Canvas dedicated to video display.
 * Here, UpdateVideo() is no longer called systematically in paint().
 */
class VideoCanvas : Canvas() {
    private val logger = Logger("VideoCanvas")

    init {
        isVisible = true
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                logger.log("Canvas resized to: ${width}x${height}")
            }
        })
    }

    override fun paint(g: Graphics) {
        super.paint(g)
    }
}