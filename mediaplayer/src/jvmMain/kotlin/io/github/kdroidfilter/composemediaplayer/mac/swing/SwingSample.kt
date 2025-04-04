package io.github.kdroidfilter.composemediaplayer.mac.swing

import com.sun.jna.Pointer
import io.github.kdroidfilter.composemediaplayer.mac.SharedVideoPlayer
import io.github.kdroidfilter.composemediaplayer.util.formatTime
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * Swing component that periodically retrieves frames from the shared buffer.
 * Optimized to reduce CPU usage by adapting to the video's native frame rate
 * and screen refresh rate.
 */
class VideoPlayerComponent : JPanel() {
    private var playerPtr: Pointer? = null
    private var bufferedImage: BufferedImage? = null
    private var frameTimer: Timer? = null
    private var isPlaying: Boolean = false

    // Cached frame rate values
    private var videoFrameRate: Float = 0.0f
    private var screenRefreshRate: Float = 0.0f
    private var captureFrameRate: Float = 0.0f

    init {
        background = Color.BLACK
        preferredSize = Dimension(640, 360)
    }

    override fun addNotify() {
        super.addNotify()
        if (playerPtr == null) {
            initPlayer()
        }
    }

    override fun removeNotify() {
        stopRefreshTimer()
        disposePlayer()
        super.removeNotify()
    }

    private fun initPlayer() {
        println("Initializing the native VideoPlayer with shared buffer...")
        playerPtr = SharedVideoPlayer.INSTANCE.createVideoPlayer()
        if (playerPtr == null) {
            System.err.println("Failed to create the native video player.")
        }
    }

    /**
     * Updates frame rate information from the native player
     */
    private fun updateFrameRateInfo() {
        playerPtr?.let { ptr ->
            videoFrameRate = SharedVideoPlayer.INSTANCE.getVideoFrameRate(ptr)
            screenRefreshRate = SharedVideoPlayer.INSTANCE.getScreenRefreshRate(ptr)
            captureFrameRate = SharedVideoPlayer.INSTANCE.getCaptureFrameRate(ptr)
            println("Frame rates - Video: $videoFrameRate fps, Screen: $screenRefreshRate Hz, Capture: $captureFrameRate fps")
        }
    }

    /**
     * Starts the frame refresh timer at optimized frame rate
     */
    private fun startRefreshTimer() {
        stopRefreshTimer()
        updateFrameRateInfo()

        // Calculate refresh interval based on actual capture frame rate
        val refreshInterval = if (captureFrameRate > 0) (1000.0f / captureFrameRate).toInt() else 16

        isPlaying = true
        frameTimer = Timer(refreshInterval) { updateFrame() }
        frameTimer?.start()

        println("Started frame timer with interval: $refreshInterval ms")
    }

    /**
     * Stops the frame refresh timer to reduce CPU usage when paused
     */
    private fun stopRefreshTimer() {
        frameTimer?.stop()
        frameTimer = null
        isPlaying = false
    }

    /**
     * Updates the current frame from the shared buffer
     */
    private fun updateFrame() {
        if (playerPtr == null) return

        val width = SharedVideoPlayer.INSTANCE.getFrameWidth(playerPtr)
        val height = SharedVideoPlayer.INSTANCE.getFrameHeight(playerPtr)
        if (width <= 0 || height <= 0) return

        val framePtr = SharedVideoPlayer.INSTANCE.getLatestFrame(playerPtr) ?: return

        if (bufferedImage == null || bufferedImage!!.width != width || bufferedImage!!.height != height) {
            bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        }

        val pixelArray = (bufferedImage!!.raster.dataBuffer as DataBufferInt).data
        val byteBuffer: ByteBuffer = framePtr.getByteBuffer(0, width.toLong() * height * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intBuffer: IntBuffer = byteBuffer.asIntBuffer()
        intBuffer.get(pixelArray)

        repaint()
    }

    /**
     * Performs a single frame update without starting the timer
     * Used when we need to update the display but not continuously
     */
    private fun updateSingleFrame() {
        if (!isPlaying) {
            updateFrame()
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        bufferedImage?.let {
            val panelWidth = width
            val scaleFactor = panelWidth.toDouble() / it.width
            val scaledHeight = (it.height * scaleFactor).toInt()
            val y = (height - scaledHeight) / 2
            g.drawImage(it, 0, y, panelWidth, scaledHeight, null)
        }
    }

    /**
     * Opens a media via its URI (local or URL).
     */
    fun openMedia(uri: String) {
        if (playerPtr == null) {
            initPlayer()
            SwingUtilities.invokeLater { openMedia(uri) }
            return
        }
        println("Opening media: $uri")
        SharedVideoPlayer.INSTANCE.openUri(playerPtr, uri)

        // Update frame rate information
        updateFrameRateInfo()

        // Get the initial frame without starting the timer
        updateSingleFrame()
    }

    /**
     * Starts playback.
     */
    fun play() {
        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.playVideo(it)
            startRefreshTimer()
        }
    }

    /**
     * Pauses playback.
     */
    fun pause() {
        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.pauseVideo(it)
            stopRefreshTimer()
            // Get the frame at pause position
            updateSingleFrame()
        }
    }

    /**
     * Sets the audio volume (0.0 to 1.0).
     */
    fun setVolume(volume: Float) {
        playerPtr?.let { SharedVideoPlayer.INSTANCE.setVolume(it, volume) }
    }

    /**
     * Gets the current audio volume (0.0 to 1.0).
     */
    fun getVolume(): Float {
        return playerPtr?.let { SharedVideoPlayer.INSTANCE.getVolume(it) } ?: 1.0f
    }

    /**
     * Returns the video's native frame rate in fps.
     */
    fun getVideoFrameRate(): Float {
        return playerPtr?.let { SharedVideoPlayer.INSTANCE.getVideoFrameRate(it) } ?: 0.0f
    }

    /**
     * Returns the screen's refresh rate in Hz.
     */
    fun getScreenRefreshRate(): Float {
        return playerPtr?.let { SharedVideoPlayer.INSTANCE.getScreenRefreshRate(it) } ?: 0.0f
    }

    /**
     * Returns the actual capture frame rate being used (minimum of video and screen rates).
     */
    fun getCaptureFrameRate(): Float {
        return playerPtr?.let { SharedVideoPlayer.INSTANCE.getCaptureFrameRate(it) } ?: 0.0f
    }

    /**
     * Returns the video duration in seconds.
     */
    fun getDuration(): Double {
        return playerPtr?.let { SharedVideoPlayer.INSTANCE.getVideoDuration(it) } ?: 0.0
    }

    /**
     * Returns the current playback time in seconds.
     */
    fun getCurrentTime(): Double {
        return playerPtr?.let { SharedVideoPlayer.INSTANCE.getCurrentTime(it) } ?: 0.0
    }

    /**
     * Seeks to the specified time (in seconds).
     */
    fun seekTo(time: Double) {
        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.seekTo(it, time)
            // Update the display after seeking
            updateSingleFrame()
        }
    }

    private fun disposePlayer() {
        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.disposeVideoPlayer(it)
            playerPtr = null
            println("Native video player released.")
        }
    }
}

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