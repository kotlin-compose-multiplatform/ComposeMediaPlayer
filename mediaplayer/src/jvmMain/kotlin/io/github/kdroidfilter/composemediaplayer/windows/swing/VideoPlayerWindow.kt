package io.github.kdroidfilter.composemediaplayer.windows.swing

import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef
import io.github.kdroidfilter.composemediaplayer.util.formatTime
import io.github.kdroidfilter.composemediaplayer.util.logger
import io.github.kdroidfilter.composemediaplayer.windows.MediaPlayerLib
import io.github.kdroidfilter.composemediaplayer.windows.ui.VideoCanvas
import io.github.kdroidfilter.composemediaplayer.windows.wrapper.AudioControl
import io.github.kdroidfilter.composemediaplayer.windows.wrapper.MediaPlayerSlider
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.Timer
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.system.exitProcess

internal class VideoPlayerWindow : JFrame("KDroidFilter Media Player") {
    private var isPaused = false
    private var renderTimer: Timer? = null
    private var progressTimer: Timer? = null

    private val videoCanvas = VideoCanvas()
    private val controlPanel = JPanel()
    private val openButton = JButton("Open").apply { isEnabled = true }
    private val openUrlButton = JButton("Open URL").apply { isEnabled = true }
    private val playPauseButton = JButton("Play").apply { isEnabled = false }
    private val stopButton = JButton("Stop").apply { isEnabled = false }

    // Ajout du LoaderOverlay
    private val loaderOverlay = LoaderOverlay()
    private var isLoading = false

    // Progress Slider
    private val progressSlider = JSlider(JSlider.HORIZONTAL, 0, 1000, 0).apply {
        preferredSize = Dimension(300, 20)
        toolTipText = "Progress"
    }
    private val timeLabel = JLabel("00:00:00 / 00:00:00")
    private val mediaSlider = MediaPlayerSlider(MediaPlayerLib.INSTANCE)
    private var userIsSeeking = false

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
            startProgressTimer()
        } catch (e: Exception) {
            logger.error(e) { "Initialization error" }
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
        // Création du LayeredPane pour gérer le loader
        val layeredPane = JLayeredPane()
        layeredPane.layout = object : LayoutManager {
            override fun addLayoutComponent(name: String?, comp: Component?) {}
            override fun removeLayoutComponent(comp: Component?) {}
            override fun preferredLayoutSize(parent: Container?) = parent?.size ?: Dimension(0, 0)
            override fun minimumLayoutSize(parent: Container?) = preferredLayoutSize(parent)
            override fun layoutContainer(parent: Container?) {
                parent?.components?.forEach { it.setBounds(0, 0, parent.width, parent.height) }
            }
        }

        layeredPane.add(videoCanvas, JLayeredPane.DEFAULT_LAYER)
        layeredPane.add(loaderOverlay, JLayeredPane.POPUP_LAYER)
        loaderOverlay.isVisible = false

        add(layeredPane, BorderLayout.CENTER)

        controlPanel.layout = BoxLayout(controlPanel, BoxLayout.X_AXIS)
        controlPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        // Media Controls
        controlPanel.add(openButton)
        controlPanel.add(Box.createHorizontalStrut(5))
        controlPanel.add(openUrlButton)
        controlPanel.add(Box.createHorizontalStrut(5))
        controlPanel.add(playPauseButton)
        controlPanel.add(Box.createHorizontalStrut(5))
        controlPanel.add(stopButton)
        controlPanel.add(Box.createHorizontalStrut(10))

        // Progress Controls
        controlPanel.add(timeLabel)
        controlPanel.add(Box.createHorizontalStrut(5))
        controlPanel.add(progressSlider)

        controlPanel.add(Box.createHorizontalGlue())

        // Audio Controls
        controlPanel.add(muteButton)
        controlPanel.add(Box.createHorizontalStrut(5))
        controlPanel.add(volumeSlider)

        add(controlPanel, BorderLayout.SOUTH)
    }

    private fun setupListeners() {
        openButton.addActionListener { openFile() }
        openUrlButton.addActionListener { openUrlDialog() }
        playPauseButton.addActionListener { togglePlayPause() }
        stopButton.addActionListener { stopPlayback() }

        // Progress Slider Listeners
        progressSlider.addChangeListener { e ->
            if (progressSlider.valueIsAdjusting) {
                userIsSeeking = true
                updateTimeLabel()
            } else if (userIsSeeking) {
                userIsSeeking = false
                val progress = progressSlider.value / 1000f
                mediaSlider.setProgress(progress)
            }
        }
    }

    private fun startProgressTimer() {
        progressTimer?.stop()
        progressTimer = Timer(100) { // Update every 100ms
            if (!userIsSeeking && MediaPlayerLib.INSTANCE.IsInitialized()) {
                updateProgress()
                updateLoadingState()
            }
        }
        progressTimer?.start()
    }

    private fun updateLoadingState() {
        val loading = MediaPlayerLib.INSTANCE.IsLoading()
        if (loading != isLoading) {
            isLoading = loading
            SwingUtilities.invokeLater {
                loaderOverlay.isVisible = isLoading
                if (!isLoading) {
                    revalidate()
                    repaint()
                }
            }
        }
    }


    private fun updateProgress() {
        mediaSlider.getProgress()?.let { progress ->
            progressSlider.value = (progress * 1000).toInt()
            updateTimeLabel()
        }
    }

    private fun updateTimeLabel() {
        val currentSeconds = mediaSlider.getCurrentPositionInSeconds() ?: 0.0
        val totalSeconds = mediaSlider.getDurationInSeconds() ?: 0.0

        timeLabel.text = String.format(
            "%s / %s",
            formatTime(currentSeconds),
            formatTime(totalSeconds)
        )
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
                logger.debug {  "Volume changed to: ${volumeSlider.value}%" }
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
        logger.debug { "Render timer started (30 FPS)" }
    }

    private fun createMediaCallback(): MediaPlayerLib.MediaPlayerCallback {
        return MediaPlayerLib.MediaPlayerCallback { eventType, hr ->
            SwingUtilities.invokeLater {
                logger.debug { "Media Event: ${eventTypeToString(eventType)} (0x${hr.toString(16)})" }
                when (eventType) {
                    MediaPlayerLib.MP_EVENT_MEDIAITEM_CREATED -> handleMediaItemCreated(hr)
                    MediaPlayerLib.MP_EVENT_MEDIAITEM_SET -> handleMediaItemSet(hr)
                    MediaPlayerLib.MP_EVENT_PLAYBACK_STARTED -> handlePlaybackStarted()
                    MediaPlayerLib.MP_EVENT_PLAYBACK_PAUSED -> handlePlaybackPaused()
                    MediaPlayerLib.MP_EVENT_PLAYBACK_STOPPED -> handlePlaybackStopped()
                    MediaPlayerLib.MP_EVENT_PLAYBACK_ERROR -> handlePlaybackError(hr)
                    MediaPlayerLib.MP_EVENT_LOADING_STARTED -> {
                        isLoading = true
                        loaderOverlay.isVisible = true
                    }
                    MediaPlayerLib.MP_EVENT_LOADING_COMPLETE -> {
                        isLoading = false
                        loaderOverlay.isVisible = false
                    }
                }
            }
        }
    }

    private fun initializeMediaPlayer() {
        SwingUtilities.invokeLater {
            try {
                Thread.sleep(200)
                val hwnd = Native.getComponentPointer(videoCanvas)
                logger.debug { "Initializing Media Player with HWND: $hwnd" }

                if (hwnd == null) {
                    throw RuntimeException("Unable to retrieve the Canvas HWND")
                }

                val result = MediaPlayerLib.INSTANCE.InitializeMediaPlayer(WinDef.HWND(hwnd), mediaCallback)
                if (!checkHResult(result)) {
                    throw RuntimeException("Media player initialization failed: 0x${result.toString(16)}")
                }

                logger.debug { "Media Player successfully initialized" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to initialize the media player" }
                showError("Initialization error", -1)
                exitProcess(1)
            }
        }
    }

    private fun openUrlDialog() {
        val dialog = JDialog(this, "Open URL", true)
        dialog.layout = BorderLayout(10, 10)

        val urlPanel = JPanel(BorderLayout(5, 5))
        urlPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val urlField = JTextField(30)
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT))

        val openButton = JButton("Open")
        val cancelButton = JButton("Cancel")

        urlPanel.add(JLabel("Enter URL:"), BorderLayout.NORTH)
        urlPanel.add(urlField, BorderLayout.CENTER)

        buttonPanel.add(openButton)
        buttonPanel.add(cancelButton)

        dialog.add(urlPanel, BorderLayout.CENTER)
        dialog.add(buttonPanel, BorderLayout.SOUTH)

        openButton.addActionListener {
            val url = urlField.text.trim()
            if (url.isNotEmpty()) {
                playUrl(url)
                dialog.dispose()
            } else {
                JOptionPane.showMessageDialog(
                    dialog,
                    "Please enter a valid URL",
                    "Invalid URL",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }

        cancelButton.addActionListener {
            dialog.dispose()
        }

        urlField.addActionListener {
            openButton.doClick()
        }

        dialog.pack()
        dialog.setLocationRelativeTo(this)
        dialog.isResizable = false
        dialog.defaultCloseOperation = DISPOSE_ON_CLOSE
        dialog.isVisible = true
    }

    private fun playUrl(url: String) {
        if (!MediaPlayerLib.INSTANCE.IsInitialized()) {
            error { "The media player is not initialized." }
            return
        }

        try {
            logger.debug { "Attempting to play URL: $url" }
            val result = MediaPlayerLib.INSTANCE.PlayURL(WString(url))
            if (!checkHResult(result)) {
                error { "Failed to play URL: 0x${result.toString(16)}" }
                showError("Error opening URL", result)
            } else {
                logger.debug { "PlayURL successfully called." }
                playPauseButton.isEnabled = true
                stopButton.isEnabled = true
                progressSlider.value = 0
            }
        } catch (e: Exception) {
            logger.error(e) { "Error playing URL" }
            showError("Error playing URL", -1)
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
            error { "The media player is not initialized." }
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            error { "File does not exist: $filePath" }
            showError("File does not exist", -1)
            return
        }

        val extension = file.extension.lowercase(Locale.getDefault())
        if (extension !in setOf("mp4", "avi", "wmv", "mkv", "mov")) {
            error { "Unsupported file format: $extension" }
            showError("Unsupported file format", -1)
            return
        }

        logger.debug { "Attempting to play file: $filePath" }
        val result = MediaPlayerLib.INSTANCE.PlayFile(WString(file.absolutePath))
        if (!checkHResult(result)) {
            error { "Failed to play file: 0x${result.toString(16)}" }
            showError("Error opening file", result)
        } else {
            logger.debug { "PlayFile successfully called." }
            playPauseButton.isEnabled = true
            stopButton.isEnabled = true
            progressSlider.value = 0
        }
    }

    private fun togglePlayPause() {
        val result = if (isPaused) {
            MediaPlayerLib.INSTANCE.ResumePlayback()
        } else {
            MediaPlayerLib.INSTANCE.PausePlayback()
        }

        if (checkHResult(result)) {
            logger.debug { "Requested state change to: ${if (isPaused) "Playing" else "Paused"}" }
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
            logger.debug { "Mute toggled: $isMuted" }
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
        logger.debug { "handleMediaItemCreated, HR: 0x${hr.toString(16)}" }
    }

    private fun handleMediaItemSet(hr: Int) {
        logger.debug { "handleMediaItemSet, HR: 0x${hr.toString(16)}" }
        if (checkHResult(hr)) {
            playPauseButton.isEnabled = true
            stopButton.isEnabled = true
            progressSlider.value = 0
            updateTimeLabel()
        }
    }

    private fun handlePlaybackStarted() {
        logger.debug { "Playback started" }
        isPaused = false
        playPauseButton.text = "Pause"
        playPauseButton.isEnabled = true
        stopButton.isEnabled = true
    }

    private fun handlePlaybackPaused() {
        logger.debug { "Playback paused" }
        isPaused = true
        playPauseButton.text = "Play"
        playPauseButton.isEnabled = true
        stopButton.isEnabled = true
    }

    private fun handlePlaybackStopped() {
        logger.debug { "Playback stopped" }
        isPaused = false
        playPauseButton.text = "Play"
        playPauseButton.isEnabled = false
        stopButton.isEnabled = false
        progressSlider.value = 0
        updateTimeLabel()
    }

    private fun handlePlaybackError(hr: Int) {
        error { "Playback error: 0x${hr.toString(16)}" }
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
            error { "HRESULT Error: 0x${hr.toString(16)} - $errorMessage" }
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
            MediaPlayerLib.MP_EVENT_PLAYBACK_PAUSED -> "PLAYBACK_PAUSED"
            MediaPlayerLib.MP_EVENT_PLAYBACK_STOPPED -> "PLAYBACK_STOPPED"
            MediaPlayerLib.MP_EVENT_PLAYBACK_ERROR -> "PLAYBACK_ERROR"
            MediaPlayerLib.MP_EVENT_LOADING_STARTED -> "LOADING_STARTED"
            MediaPlayerLib.MP_EVENT_LOADING_COMPLETE -> "LOADING_COMPLETE"
            else -> "UNKNOWN_EVENT($eventType)"
        }
    }

    private fun cleanup() {
        logger.debug { "Cleaning up resources..." }
        renderTimer?.stop()
        progressTimer?.stop()
        audioControl.setMute(false)
        loaderOverlay.stopAnimation()
        MediaPlayerLib.INSTANCE.CleanupMediaPlayer()
        dispose()
    }
}