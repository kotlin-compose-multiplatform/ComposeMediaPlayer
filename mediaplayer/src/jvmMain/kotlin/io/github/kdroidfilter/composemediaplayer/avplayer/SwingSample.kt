package io.github.kdroidfilter.composemediaplayer.avplayer

import io.github.kdroidfilter.composemediaplayer.sharedbuffer.VideoPlayerComponent
import io.github.kdroidfilter.composemediaplayer.util.formatTime
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * Swing Application.
 */
fun main() {
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val frame = JFrame("VideoPlayer Demo with Shared Buffer")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(900, 800)
        frame.layout = BorderLayout()

        val playerComponent = VideoPlayerComponent()

        // Panel for the progress bar
        val progressPanel = JPanel(BorderLayout())
        val progressSlider = JSlider(0, 1000, 0)
        progressSlider.isEnabled = false
        progressPanel.add(progressSlider, BorderLayout.CENTER)
        val progressLabel = JLabel("00:00 / 00:00")
        progressPanel.add(progressLabel, BorderLayout.EAST)

        // Control panel with buttons and URL field
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

        // Listener to load a URL
        btnOpenURL.addActionListener {
            val url = urlField.text
            if (url.isNotBlank()) {
                playerComponent.openMedia(url)
                playerComponent.play()
                // After a short delay, initialize the progress bar
                Timer(1000) {
                    val duration = playerComponent.getDuration()
                    if (duration > 0) {
                        progressSlider.maximum = (duration * 1000).toInt() // in milliseconds
                        progressSlider.isEnabled = true
                    }
                }.start()
            } else {
                JOptionPane.showMessageDialog(frame, "Please enter a valid URL.", "Error", JOptionPane.ERROR_MESSAGE)
            }
        }

        // Listener to open a local file
        btnOpenFile.addActionListener {
            val chooser = JFileChooser()
            if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                val file = chooser.selectedFile
                playerComponent.openMedia(file.absolutePath)
                playerComponent.play()
                Timer(1000) {
                    val duration = playerComponent.getDuration()
                    if (duration > 0) {
                        progressSlider.maximum = (duration * 1000).toInt()
                        progressSlider.isEnabled = true
                    }
                }.start()
            }
        }

        btnPlay.addActionListener { playerComponent.play() }
        btnPause.addActionListener { playerComponent.pause() }
        btnQuit.addActionListener { frame.dispose() }

        // Timer to update the progress bar and label
        val progressTimer = Timer(500) {
            val current = playerComponent.getCurrentTime()
            val duration = playerComponent.getDuration()
            if (duration > 0) {
                progressSlider.value = (current * 1000).toInt()
                progressLabel.text = "${formatTime(current)} / ${formatTime(duration)}"
            }
        }
        progressTimer.start()

        // Listener for the slider to allow seeking
        progressSlider.addChangeListener(object : ChangeListener {
            var isAdjusting = false
            override fun stateChanged(e: ChangeEvent?) {
                if (progressSlider.valueIsAdjusting) {
                    isAdjusting = true
                } else if (isAdjusting) {
                    // The user has finished adjusting
                    val seekTime = progressSlider.value / 1000.0
                    playerComponent.seekTo(seekTime)
                    isAdjusting = false
                }
            }
        })

        // Group the progress bar and control panel in a common panel at the bottom
        val bottomPanel = JPanel()
        bottomPanel.layout = BoxLayout(bottomPanel, BoxLayout.Y_AXIS)
        bottomPanel.add(progressPanel)
        bottomPanel.add(controlPanel)

        // Add the components to the frame
        frame.add(playerComponent, BorderLayout.CENTER)
        frame.add(bottomPanel, BorderLayout.SOUTH)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}
