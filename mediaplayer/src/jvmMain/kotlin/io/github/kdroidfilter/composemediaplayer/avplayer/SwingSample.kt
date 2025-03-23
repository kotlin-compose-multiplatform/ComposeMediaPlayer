package io.github.kdroidfilter.composemediaplayer.avplayer

import io.github.kdroidfilter.composemediaplayer.sharedbuffer.VideoPlayerComponent
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

/**
 * Enhanced Swing application with improved user interface.
 */
fun main() {
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val frame = JFrame("Shared Buffer VideoPlayer Demo")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(900, 700)
        frame.layout = BorderLayout()

        val playerComponent = VideoPlayerComponent()

        val controlPanel = JPanel()
        controlPanel.layout = GridBagLayout()
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            fill = GridBagConstraints.HORIZONTAL
        }

        val urlField = JTextField(40)
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.gridwidth = 3
        controlPanel.add(urlField, gbc)

        val btnOpenURL = JButton("Load URL")
        gbc.gridx = 3
        gbc.gridy = 0
        gbc.gridwidth = 1
        controlPanel.add(btnOpenURL, gbc)

        val btnOpenFile = JButton("Open Local File")
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.gridwidth = 1
        controlPanel.add(btnOpenFile, gbc)

        val btnPlay = JButton("Play")
        gbc.gridx = 1
        gbc.gridy = 1
        controlPanel.add(btnPlay, gbc)

        val btnPause = JButton("Pause")
        gbc.gridx = 2
        gbc.gridy = 1
        controlPanel.add(btnPause, gbc)

        val btnQuit = JButton("Quit")
        gbc.gridx = 3
        gbc.gridy = 1
        controlPanel.add(btnQuit, gbc)

        btnOpenURL.addActionListener {
            val url = urlField.text
            if (url.isNotBlank()) {
                playerComponent.openMedia(url)
                playerComponent.play()
            } else {
                JOptionPane.showMessageDialog(frame, "Please enter a valid URL.", "Error", JOptionPane.ERROR_MESSAGE)
            }
        }

        btnOpenFile.addActionListener {
            val chooser = JFileChooser()
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                playerComponent.openMedia(file.absolutePath)
                playerComponent.play()
            }
        }

        btnPlay.addActionListener { playerComponent.play() }
        btnPause.addActionListener { playerComponent.pause() }
        btnQuit.addActionListener { frame.dispose() }

        frame.add(playerComponent, BorderLayout.CENTER)
        frame.add(controlPanel, BorderLayout.SOUTH)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}
