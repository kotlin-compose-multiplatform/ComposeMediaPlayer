package io.github.kdroidfilter.composemediaplayer.avplayer

import io.github.kdroidfilter.composemediaplayer.sharedbuffer.VideoPlayerComponent
import io.github.kdroidfilter.composemediaplayer.util.formatTime
import java.awt.BorderLayout
import java.awt.Dimension
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

        // Volume control panel
        val volumePanel = JPanel(BorderLayout())
        volumePanel.border = BorderFactory.createTitledBorder("Volume")

        // Create a volume slider with range 0-100
        val volumeSlider = JSlider(SwingConstants.HORIZONTAL, 0, 100, 100)
        volumeSlider.preferredSize = Dimension(200, volumeSlider.preferredSize.height)
        volumeSlider.paintTicks = true
        volumeSlider.paintLabels = true
        volumeSlider.majorTickSpacing = 25
        volumeSlider.minorTickSpacing = 5

        // Volume icon label
        val volumeIconLabel = JLabel("🔊")
        volumeIconLabel.horizontalAlignment = SwingConstants.CENTER
        volumeIconLabel.border = BorderFactory.createEmptyBorder(0, 5, 0, 5)

        // Volume percentage label
        val volumePercentLabel = JLabel("100%")
        volumePercentLabel.horizontalAlignment = SwingConstants.RIGHT
        volumePercentLabel.border = BorderFactory.createEmptyBorder(0, 5, 0, 5)

        // Volume mute button
        val muteButton = JToggleButton("Mute")

        // Add components to volume panel
        volumePanel.add(volumeIconLabel, BorderLayout.WEST)
        volumePanel.add(volumeSlider, BorderLayout.CENTER)
        volumePanel.add(volumePercentLabel, BorderLayout.EAST)

        // Setup volume slider listener
        volumeSlider.addChangeListener {
            val volumeValue = volumeSlider.value / 100.0f
            playerComponent.setVolume(volumeValue)
            volumePercentLabel.text = "${volumeSlider.value}%"

            // Update volume icon based on level
            volumeIconLabel.text = when {
                volumeValue == 0f -> "🔇"  // Muted
                volumeValue < 0.3f -> "🔈"  // Low volume
                volumeValue < 0.7f -> "🔉"  // Medium volume
                else -> "🔊"  // High volume
            }

            // Update mute button state
            if (volumeValue == 0f && !muteButton.isSelected) {
                muteButton.isSelected = true
            } else if (volumeValue > 0f && muteButton.isSelected) {
                muteButton.isSelected = false
            }
        }

        // Setup mute button listener
        var lastVolumeBeforeMute = 100
        muteButton.addActionListener {
            if (muteButton.isSelected) {
                lastVolumeBeforeMute = volumeSlider.value
                volumeSlider.value = 0
            } else {
                volumeSlider.value = if (lastVolumeBeforeMute > 0) lastVolumeBeforeMute else 100
            }
        }

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

        // Add mute button
        gbc.gridx = 4
        gbc.gridy = 1
        controlPanel.add(muteButton, gbc)

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

                // Initialize volume based on the player's current volume
                val currentVolume = (playerComponent.getVolume() * 100).toInt()
                volumeSlider.value = currentVolume
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

        // Group the panels in a common panel at the bottom
        val bottomPanel = JPanel()
        bottomPanel.layout = BoxLayout(bottomPanel, BoxLayout.Y_AXIS)
        bottomPanel.add(progressPanel)
        bottomPanel.add(volumePanel)  // Add the volume panel
        bottomPanel.add(controlPanel)

        // Add the components to the frame
        frame.add(playerComponent, BorderLayout.CENTER)
        frame.add(bottomPanel, BorderLayout.SOUTH)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}