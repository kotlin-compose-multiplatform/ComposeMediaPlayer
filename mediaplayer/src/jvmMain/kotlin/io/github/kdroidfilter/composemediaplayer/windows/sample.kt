package io.github.kdroidfilter.composemediaplayer.windows

import java.io.File
import java.util.*
import javax.swing.JOptionPane

fun main() {
    try {
        // Enable verbose native debug output
        System.setProperty("jna.debug_load", "true")

        val mediaPlayer = MediaPlayer()
        mediaPlayer.initialize()

        // Load your video file
        val mediaUrl = "C:\\Users\\Elyahou Gambache\\Desktop\\videoplayback.mp4"
        println("Attempting to load media from: $mediaUrl")

        // Verify file exists and is readable
        val mediaFile = File(mediaUrl)
        println("File exists: ${mediaFile.exists()}")
        println("File readable: ${mediaFile.canRead()}")
        println("File size: ${mediaFile.length()} bytes")
        println("File extension: ${mediaFile.extension.lowercase(Locale.getDefault())}")

        mediaPlayer.loadMedia(mediaUrl)

    } catch (e: Exception) {
        e.printStackTrace()
        JOptionPane.showMessageDialog(
            null,
            "Fatal error: ${e.message}",
            "Error",
            JOptionPane.ERROR_MESSAGE
        )
    }
}