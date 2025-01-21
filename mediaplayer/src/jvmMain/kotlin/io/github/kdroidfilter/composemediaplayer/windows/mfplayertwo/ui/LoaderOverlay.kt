package io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.ui

import java.awt.*
import javax.swing.JPanel
import javax.swing.Timer

internal class LoaderOverlay : JPanel() {
    private var angle = 0f
    private val timer = Timer(50) { // 20 FPS animation
        angle += 10f
        if (angle >= 360f) angle = 0f
        repaint()
    }

    init {
        isOpaque = false
        timer.start()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Fond semi-transparent
        g2.color = Color(0, 0, 0, 128)
        g2.fillRect(0, 0, width, height)

        // Dessin du loader
        val centerX = width / 2
        val centerY = height / 2
        val radius = 30

        g2.color = Color.WHITE
        g2.stroke = BasicStroke(3f)

        val start = Math.toRadians(angle.toDouble())
        val extent = Math.toRadians(270.0) // 3/4 d'un cercle

        g2.drawArc(
            centerX - radius,
            centerY - radius,
            radius * 2,
            radius * 2,
            Math.toDegrees(start).toInt(),
            Math.toDegrees(extent).toInt()
        )

        // Texte "Loading..."
        g2.font = Font("Arial", Font.BOLD, 16)
        val text = "Loading..."
        val metrics = g2.fontMetrics
        val textWidth = metrics.stringWidth(text)
        g2.drawString(
            text,
            centerX - textWidth / 2,
            centerY + radius + metrics.height + 10
        )
    }

    fun stopAnimation() {
        timer.stop()
    }
}