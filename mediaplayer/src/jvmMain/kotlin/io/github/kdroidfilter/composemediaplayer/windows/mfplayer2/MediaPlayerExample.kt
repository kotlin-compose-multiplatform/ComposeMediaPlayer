package io.github.kdroidfilter.composemediaplayer.windows.mfplayer2

import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.system.exitProcess

fun main() {
    try {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        SwingUtilities.invokeLater {
            VideoPlayerWindow().apply {
                pack()
                isVisible = true
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        exitProcess(1)
    }
}
