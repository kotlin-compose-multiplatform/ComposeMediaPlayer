package io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.Timer
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.system.exitProcess

class VideoPlayerWindow : JFrame("KDroidFilter Media Player") {
    private val logger = Logger("VideoPlayer")
    private var isPaused = false
    private var renderTimer: Timer? = null

    private val videoCanvas = VideoCanvas()
    private val controlPanel = JPanel()
    private val openButton = JButton("Open").apply { isEnabled = true }
    private val playPauseButton = JButton("Play").apply { isEnabled = false }
    private val stopButton = JButton("Stop").apply { isEnabled = false }

    // Audio Controls
    private val audioControl = AudioControl(MediaPlayerLib.INSTANCE)
    private val volumeSlider = JSlider(JSlider.HORIZONTAL, 0, 100, 100).apply {
        preferredSize = Dimension(100, 20)
        toolTipText = "Volume"
    }
    private val muteButton = JButton().apply {
        icon = createVolumeIcon(true)
        toolTipText = "Mute"
    }
    private var isMuted = false

    private val mediaCallback = createMediaCallback()

    init {
        try {
            setupWindow()
            setupUI()
            setupListeners()
            initializeMediaPlayer()
            initializeAudioControls()
            startRenderTimer()
        } catch (e: Exception) {
            logger.error("Initialization error", e)
            showError("Initialization error", -1)
            exitProcess(1)
        }
    }

    private fun setupWindow() {
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(800, 600)
        preferredSize = Dimension(1280, 720)
        setLocationRelativeTo(null)
        layout = BorderLayout(0, 5)

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                cleanup()
            }
        })
    }

    private fun setupUI() {
        add(videoCanvas, BorderLayout.CENTER)

        controlPanel.layout = BoxLayout(controlPanel, BoxLayout.X_AXIS)
        controlPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        controlPanel.add(openButton)
        controlPanel.add(Box.createHorizontalStrut(5))
        controlPanel.add(playPauseButton)
        controlPanel.add(Box.createHorizontalStrut(5))
        controlPanel.add(stopButton)

        controlPanel.add(Box.createHorizontalGlue())

        controlPanel.add(muteButton)
        controlPanel.add(Box.createHorizontalStrut(5))
        controlPanel.add(volumeSlider)

        add(controlPanel, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        openButton.addActionListener { openFile() }
        playPauseButton.addActionListener { togglePlayPause() }
        stopButton.addActionListener { stopPlayback() }
    }

    private fun initializeAudioControls() {
        audioControl.getVolume()?.let { volume ->
            volumeSlider.value = (volume * 100).toInt()
        }

        audioControl.getMute()?.let { muted ->
            isMuted = muted
            updateMuteButton()
        }

        volumeSlider.addChangeListener { e ->
            if (!volumeSlider.valueIsAdjusting) {
                val volumeValue = volumeSlider.value / 100f
                audioControl.setVolume(volumeValue)
                logger.log("Volume changed to: ${volumeSlider.value}%")
            }
        }

        muteButton.addActionListener {
            toggleMute()
        }
    }

    private fun startRenderTimer() {
        renderTimer?.stop()
        renderTimer = Timer(60) {
            if (MediaPlayerLib.INSTANCE.IsInitialized() && MediaPlayerLib.INSTANCE.HasVideo()) {
                videoCanvas.repaint()
            }
        }
        renderTimer?.start()
        logger.log("Render timer started (30 FPS)")
    }

    private fun createMediaCallback(): MediaPlayerLib.MediaPlayerCallback {
        return MediaPlayerLib.MediaPlayerCallback { eventType, hr ->
            SwingUtilities.invokeLater {
                logger.log("Media Event: ${eventTypeToString(eventType)} (0x${hr.toString(16)})")
                when (eventType) {
                    MediaPlayerLib.MP_EVENT_MEDIAITEM_CREATED -> handleMediaItemCreated(hr)
                    MediaPlayerLib.MP_EVENT_MEDIAITEM_SET -> handleMediaItemSet(hr)
                    MediaPlayerLib.MP_EVENT_PLAYBACK_STARTED -> handlePlaybackStarted()
                    MediaPlayerLib.MP_EVENT_PLAYBACK_STOPPED -> handlePlaybackStopped()
                    MediaPlayerLib.MP_EVENT_PLAYBACK_ERROR -> handlePlaybackError(hr)
                }
            }
        }
    }

    private fun initializeMediaPlayer() {
        SwingUtilities.invokeLater {
            try {
                Thread.sleep(200)
                val hwnd = Native.getComponentPointer(videoCanvas)
                logger.log("Initializing Media Player with HWND: $hwnd")

                if (hwnd == null) {
                    throw RuntimeException("Unable to retrieve the Canvas HWND")
                }

                val result = MediaPlayerLib.INSTANCE.InitializeMediaPlayer(WinDef.HWND(hwnd), mediaCallback)
                if (!checkHResult(result)) {
                    throw RuntimeException("Media player initialization failed: 0x${result.toString(16)}")
                }

                logger.log("Media Player successfully initialized")
            } catch (e: Exception) {
                logger.error("Failed to initialize the media player", e)
                showError("Initialization error", -1)
                exitProcess(1)
            }
        }
    }

    private fun openFile() {
        val fileChooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            fileFilter = FileNameExtensionFilter(
                "Video Files (*.mp4, *.avi, *.mkv, *.wmv)",
                "mp4", "avi", "mkv", "wmv", "mov"
            )
            currentDirectory = File(System.getProperty("user.home"))
        }

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            playFile(fileChooser.selectedFile.absolutePath)
        }
    }

    private fun playFile(filePath: String) {
        if (!MediaPlayerLib.INSTANCE.IsInitialized()) {
            logger.error("The media player is not initialized.")
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            logger.error("File does not exist: $filePath")
            showError("File does not exist", -1)
            return
        }

        val extension = file.extension.lowercase(Locale.getDefault())
        if (extension !in setOf("mp4", "avi", "wmv", "mkv", "mov")) {
            logger.error("Unsupported file format: $extension")
            showError("Unsupported file format", -1)
            return
        }

        logger.log("Attempting to play file: $filePath")
        val result = MediaPlayerLib.INSTANCE.PlayFile(WString(file.absolutePath))
        if (!checkHResult(result)) {
            logger.error("Failed to play file: 0x${result.toString(16)}")
            showError("Error opening file", result)
        } else {
            logger.log("PlayFile successfully called.")
            playPauseButton.isEnabled = true
            stopButton.isEnabled = true
        }
    }

    private fun togglePlayPause() {
        val result = if (isPaused) {
            MediaPlayerLib.INSTANCE.ResumePlayback()
        } else {
            MediaPlayerLib.INSTANCE.PausePlayback()
        }

        if (checkHResult(result)) {
            isPaused = !isPaused
            playPauseButton.text = if (isPaused) "Play" else "Pause"
            logger.log("State changed: ${if (isPaused) "Paused" else "Playing"}")
        }
    }

    private fun stopPlayback() {
        val result = MediaPlayerLib.INSTANCE.StopPlayback()
        if (checkHResult(result)) {
            handlePlaybackStopped()
        }
    }

    private fun toggleMute() {
        isMuted = !isMuted
        if (audioControl.setMute(isMuted)) {
            updateMuteButton()
            logger.log("Mute toggled: $isMuted")
        }
    }

    private fun updateMuteButton() {
        muteButton.icon = createVolumeIcon(!isMuted)
        muteButton.toolTipText = if (isMuted) "Unmute" else "Mute"
    }

    private fun createVolumeIcon(volumeOn: Boolean): ImageIcon {
        val size = 16
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()

        g2.color = Color.BLACK
        if (volumeOn) {
            g2.fillRect(4, 6, 3, 4)
            g2.drawPolygon(
                intArrayOf(7, 11, 11, 7),
                intArrayOf(4, 2, 14, 12),
                4
            )
        } else {
            g2.fillRect(4, 6, 3, 4)
            g2.drawPolygon(
                intArrayOf(7, 11, 11, 7),
                intArrayOf(4, 2, 14, 12),
                4
            )
            g2.drawLine(13, 4, 4, 13)
        }

        g2.dispose()
        return ImageIcon(image)
    }

    private fun handleMediaItemCreated(hr: Int) {
        logger.log("handleMediaItemCreated, HR: 0x${hr.toString(16)}")
    }

    private fun handleMediaItemSet(hr: Int) {
        logger.log("handleMediaItemSet, HR: 0x${hr.toString(16)}")
        if (checkHResult(hr)) {
            playPauseButton.isEnabled = true
            stopButton.isEnabled = true
        }
    }

    private fun handlePlaybackStarted() {
        logger.log("Playback started")
        isPaused = false
        playPauseButton.text = "Pause"
        playPauseButton.isEnabled = true
        stopButton.isEnabled = true
    }

    private fun handlePlaybackStopped() {
        logger.log("Playback stopped")
        isPaused = false
        playPauseButton.text = "Play"
        playPauseButton.isEnabled = false
        stopButton.isEnabled = false
    }

    private fun handlePlaybackError(hr: Int) {
        logger.error("Playback error: 0x${hr.toString(16)}")
        showError("Playback error", hr)
        handlePlaybackStopped()
    }

    private fun checkHResult(hr: Int): Boolean {
        if (hr < 0) {
            val errorMessage = when (hr) {
                -2147221008 -> "Method not implemented"
                -2147024809 -> "Invalid argument"
                -2147024891 -> "Access denied"
                else -> "Unknown error"
            }
            logger.error("HRESULT Error: 0x${hr.toString(16)} - $errorMessage")
            return false
        }
        return true
    }

    private fun showError(message: String, hr: Int) {
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(
                this,
                "$message (Code: 0x${hr.toString(16)})",
                "Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun eventTypeToString(eventType: Int): String {
        return when (eventType) {
            MediaPlayerLib.MP_EVENT_MEDIAITEM_CREATED -> "MEDIAITEM_CREATED"
            MediaPlayerLib.MP_EVENT_MEDIAITEM_SET -> "MEDIAITEM_SET"
            MediaPlayerLib.MP_EVENT_PLAYBACK_STARTED -> "PLAYBACK_STARTED"
            MediaPlayerLib.MP_EVENT_PLAYBACK_STOPPED -> "PLAYBACK_STOPPED"
            MediaPlayerLib.MP_EVENT_PLAYBACK_ERROR -> "PLAYBACK_ERROR"
            else -> "UNKNOWN_EVENT($eventType)"
        }
    }

    private fun cleanup() {
        logger.log("Cleaning up resources...")
        renderTimer?.stop()
        audioControl.setMute(false)
        MediaPlayerLib.INSTANCE.CleanupMediaPlayer()
        dispose()
    }
}