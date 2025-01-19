package io.github.kdroidfilter.composemediaplayer.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.JPanel

// Main Media Player Class
class MediaPlayer {
    private val playerState = PlayerState()
    private lateinit var frame: JFrame
    private lateinit var videoPanel: Canvas
    private lateinit var controlPanel: JPanel
    private lateinit var playButton: JButton
    private lateinit var pauseButton: JButton
    private lateinit var stopButton: JButton

    fun initialize() {
        initializeCOM()
        createUserInterface()
        initializeMediaPlayer()
    }

    private fun initializeCOM() {
        try {
            Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
            println("COM initialized successfully")
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize COM", e)
        }
    }

    private fun createUserInterface() {
        frame = JFrame("Media Player").apply {
            defaultCloseOperation = JFrame.EXIT_ON_CLOSE
            setSize(MediaPlayerConstants.DEFAULT_WINDOW_WIDTH, MediaPlayerConstants.DEFAULT_WINDOW_HEIGHT)
            layout = BorderLayout()
        }

        createVideoPanel()
        createControlPanel()

        frame.isVisible = true
        videoPanel.validate()
    }

    private fun createVideoPanel() {
        videoPanel = Canvas().apply {
            background = Color.BLACK
            preferredSize = Dimension(
                MediaPlayerConstants.DEFAULT_WINDOW_WIDTH,
                MediaPlayerConstants.DEFAULT_WINDOW_HEIGHT
            )
        }
        frame.add(videoPanel, BorderLayout.CENTER)
    }

    private fun createControlPanel() {
        controlPanel = JPanel(FlowLayout()).apply {
            playButton = JButton("Play").apply {
                addActionListener { playMedia() }
            }
            pauseButton = JButton("Pause").apply {
                addActionListener { pauseMedia() }
                isEnabled = false
            }
            stopButton = JButton("Stop").apply {
                addActionListener { stopMedia() }
                isEnabled = false
            }

            add(playButton)
            add(pauseButton)
            add(stopButton)
        }
        frame.add(controlPanel, BorderLayout.SOUTH)
    }

    private fun initializeMediaPlayer() {
        try {
            MFPlayerLibrary.INSTANCE // Verify library is loaded
            println("Native library loaded successfully")

            val hwnd = Native.getComponentPointer(videoPanel)
            println("HWND: $hwnd")

            if (hwnd == null || hwnd == Pointer.NULL) {
                throw RuntimeException("Failed to get valid HWND")
            }

            val mediaPlayerRef = PointerByReference()
            val initResult = MFPlayerLibrary.INSTANCE.MFPMediaPlayer_Init(
                hwnd,
                Pointer.createConstant(10),
                mediaPlayerRef
            )

            if (!initResult || mediaPlayerRef.value == null) {
                throw RuntimeException("Failed to initialize media player")
            }

            playerState.mediaPlayer = mediaPlayerRef.value
            println("Media player initialized: ${playerState.mediaPlayer}")

            setupWindowClosing()

        } catch (e: Exception) {
            handleError("Failed to initialize media player", e)
        }
    }

    fun loadMedia(mediaUrl: String) {
        try {
            validateMediaFile(mediaUrl)
            setupVideo(mediaUrl)
        } catch (e: Exception) {
            handleError("Failed to load media", e)
        }
    }

    private fun validateMediaFile(mediaUrl: String) {
        val mediaFile = File(mediaUrl)
        if (!mediaFile.exists() || !mediaFile.canRead()) {
            throw IllegalArgumentException("Media file does not exist or is not readable: $mediaUrl")
        }
        println("Media file validation successful: ${mediaFile.absolutePath}")
    }

    private fun setupVideo(mediaUrl: String): Boolean {
        playerState.mediaPlayer?.let { mediaPlayer ->
            try {
                println("Starting video setup for: $mediaUrl")

                // Clear existing media
                if (!MFPlayerLibrary.INSTANCE.MFPMediaPlayer_ClearMediaItem(mediaPlayer)) {
                    println("Warning: Failed to clear existing media item")
                }

                // Create new media item with error checking
                val mediaItemRef = PointerByReference()
                if (!MFPlayerLibrary.INSTANCE.MFPMediaPlayer_CreateMediaItemA(mediaPlayer, mediaUrl, 0, mediaItemRef)) {
                    println("Failed to create media item")
                    return false
                }

                val mediaItem = mediaItemRef.value
                if (mediaItem == null) {
                    println("Media item is null after creation")
                    return false
                }

                // Check if media item has video
                val hasVideo = WinDef.BOOLByReference()
                val selected = WinDef.BOOLByReference()
                if (MFPlayerLibrary.INSTANCE.MFPMediaItem_HasVideo(mediaItem, hasVideo, selected)) {
                    println("Media has video: ${hasVideo.value}, Selected: ${selected.value}")
                }

                // Set media item
                if (!MFPlayerLibrary.INSTANCE.MFPMediaPlayer_SetMediaItem(mediaPlayer, mediaItem)) {
                    println("Failed to set media item")
                    return false
                }

                // Wait briefly for media to load
                Thread.sleep(100)

                // Get media duration to ensure media is loaded
                val duration = LongByReference()
                if (MFPlayerLibrary.INSTANCE.MFPMediaPlayer_GetDuration(mediaPlayer, duration)) {
                    println("Media duration: ${duration.value} ms")
                }

                // Get video size
                checkVideoSize(mediaPlayer)

                // Set border color
                MFPlayerLibrary.INSTANCE.MFPMediaPlayer_SetBorderColor(
                    mediaPlayer,
                    MediaPlayerConstants.MFP_VIDEO_BORDER_COLOR
                )

                // Configure video rectangle with enhanced error checking
                if (!configureVideoRect(mediaPlayer)) {
                    println("Video rectangle configuration failed")
                    return false
                }

                // Update video display
                if (!MFPlayerLibrary.INSTANCE.MFPMediaPlayer_UpdateVideo(mediaPlayer)) {
                    println("Failed to update video display")
                    return false
                }

                updatePlayerControls()
                return true

            } catch (e: Exception) {
                println("Exception during video setup: ${e.message}")
                e.printStackTrace()
                return false
            }
        }
        return false
    }

    private fun configureVideoRect(mediaPlayer: Pointer): Boolean {
        try {
            // First set aspect ratio mode
            if (!MFPlayerLibrary.INSTANCE.MFPMediaPlayer_SetAspectRatioMode(
                    mediaPlayer,
                    MediaPlayerConstants.MFP_ASPECT_RATIO_PRESERVE
                )) {
                println("Failed to set aspect ratio mode")
            }

            // Create normalized rectangle
            val rect = MFVideoNormalizedRect().apply {
                left = 0.0f
                top = 0.0f
                right = 1.0f
                bottom = 1.0f
            }

            // Write structure to native memory
            rect.write()
            println("Video rectangle created with values: l=${rect.left}, t=${rect.top}, r=${rect.right}, b=${rect.bottom}")
            println("Rectangle pointer: ${rect.pointer}")

            // Set video source rectangle
            val result = MFPlayerLibrary.INSTANCE.MFPMediaPlayer_SetVideoSourceRect(mediaPlayer, rect.pointer)
            if (!result) {
                println("SetVideoSourceRect failed with error: ${Native.getLastError()}")
                return false
            }

            return true

        } catch (e: Exception) {
            println("Exception in configureVideoRect: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private fun checkVideoSize(mediaPlayer: Pointer) {
        val width = IntByReference()
        val height = IntByReference()

        // Get native video size
        if (MFPlayerLibrary.INSTANCE.MFPMediaPlayer_GetNativeVideoSize(mediaPlayer, width, height)) {
            println("Native video size: ${width.value} x ${height.value}")
        } else {
            println("Failed to get native video size")
        }

        // Get ideal video size
        if (MFPlayerLibrary.INSTANCE.MFPMediaPlayer_GetIdealVideoSize(mediaPlayer, width, height)) {
            println("Ideal video size: ${width.value} x ${height.value}")
        } else {
            println("Failed to get ideal video size")
        }
    }

    // Add this helper method to your MediaPlayer class
    private fun getPlayerState(): Int {
        val stateRef = IntByReference()
        return if (playerState.mediaPlayer?.let {
                MFPlayerLibrary.INSTANCE.MFPMediaPlayer_GetState(it, stateRef)
            } == true) {
            stateRef.value
        } else {
            -1
        }
    }

    private fun playMedia() {
        playerState.mediaPlayer?.let { mediaPlayer ->
            if (MFPlayerLibrary.INSTANCE.MFPMediaPlayer_Play(mediaPlayer)) {
                playerState.isPlaying = true
                updatePlayerControls()
            }
        }
    }

    private fun pauseMedia() {
        playerState.mediaPlayer?.let { mediaPlayer ->
            if (MFPlayerLibrary.INSTANCE.MFPMediaPlayer_Pause(mediaPlayer)) {
                playerState.isPlaying = false
                updatePlayerControls()
            }
        }
    }

    private fun stopMedia() {
        playerState.mediaPlayer?.let { mediaPlayer ->
            if (MFPlayerLibrary.INSTANCE.MFPMediaPlayer_Stop(mediaPlayer)) {
                playerState.isPlaying = false
                updatePlayerControls()
            }
        }
    }

    private fun updatePlayerControls() {
        val stateRef = IntByReference()
        playerState.mediaPlayer?.let { mediaPlayer ->
            if (MFPlayerLibrary.INSTANCE.MFPMediaPlayer_GetState(mediaPlayer, stateRef)) {
                when (stateRef.value) {
                    MediaPlayerConstants.MFP_MEDIAPLAYER_STATE_PLAYING -> {
                        playButton.isEnabled = false
                        pauseButton.isEnabled = true
                        stopButton.isEnabled = true
                    }
                    MediaPlayerConstants.MFP_MEDIAPLAYER_STATE_PAUSED -> {
                        playButton.isEnabled = true
                        pauseButton.isEnabled = false
                        stopButton.isEnabled = true
                    }
                    MediaPlayerConstants.MFP_MEDIAPLAYER_STATE_STOPPED -> {
                        playButton.isEnabled = true
                        pauseButton.isEnabled = false
                        stopButton.isEnabled = false
                    }
                    else -> {
                        playButton.isEnabled = true
                        pauseButton.isEnabled = false
                        stopButton.isEnabled = false
                    }
                }
            }
        }
    }

    private fun setupWindowClosing() {
        frame.addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {
                cleanup()
            }
        })
    }

    private fun cleanup() {
        try {
            playerState.mediaPlayer?.let { mediaPlayer ->
                MFPlayerLibrary.INSTANCE.apply {
                    MFPMediaPlayer_Stop(mediaPlayer)
                    MFPMediaPlayer_ClearMediaItem(mediaPlayer)
                    MFPMediaPlayer_Shutdown(mediaPlayer)
                }
            }
        } catch (e: Exception) {
            println("Error during cleanup: ${e.message}")
        } finally {
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    private fun handleError(message: String, error: Exception) {
        println("$message: ${error.message}")
        error.printStackTrace()
        JOptionPane.showMessageDialog(
            frame,
            "$message\n${error.message}",
            "Error",
            JOptionPane.ERROR_MESSAGE
        )
    }
}
