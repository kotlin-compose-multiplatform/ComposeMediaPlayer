package io.github.kdroidfilter.composemediaplayer.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.PointerByReference
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

class MediaPlayerExample : JFrame("MFPlayer Example - kdroidFilter") {
    private var player: MFPlayer? = null
    private val controlPanel = JPanel()
    private val videoCanvas = Canvas().apply {
        background = java.awt.Color.BLACK
        preferredSize = Dimension(800, 450)
    }
    private val playButton = JButton("Play").apply {
        preferredSize = Dimension(100, 30)
    }
    private val pauseButton = JButton("Pause").apply {
        preferredSize = Dimension(100, 30)
    }
    private val stopButton = JButton("Stop").apply {
        preferredSize = Dimension(100, 30)
    }
    private val volumeSlider = JSlider(0, 100, 50).apply {
        preferredSize = Dimension(200, 30)
    }
    private val statusLabel = JLabel("Status: Ready")

    private val callback = object : MFPlayerLibrary.IMFPCallback {
        override fun invoke(pEvent: Pointer?): Int {
            SwingUtilities.invokeLater {
                try {
                    val (videoSize, _) = player?.getNativeVideoSize() ?: return@invokeLater
                    updateStatus("Video loaded - Size: ${videoSize.cx}x${videoSize.cy}")
                } catch (e: Exception) {
                    // Ignore the error if the video is not ready yet
                }
            }
            return 0
        }
    }

    init {
        setupWindow()
        setupUI()
    }

    private fun setupWindow() {
        defaultCloseOperation = EXIT_ON_CLOSE
        size = Dimension(MFPConstants.DEFAULT_WINDOW_WIDTH, MFPConstants.DEFAULT_WINDOW_HEIGHT)
        setLocationRelativeTo(null)
    }

    private fun setupUI() {
        layout = BorderLayout(10, 10)

        controlPanel.apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createEmptyBorder(5, 10, 5, 10)
            add(playButton)
            add(Box.createRigidArea(Dimension(10, 0)))
            add(pauseButton)
            add(Box.createRigidArea(Dimension(10, 0)))
            add(stopButton)
            add(Box.createRigidArea(Dimension(20, 0)))
            add(JLabel("Volume:"))
            add(Box.createRigidArea(Dimension(10, 0)))
            add(volumeSlider)
        }

        add(videoCanvas, BorderLayout.CENTER)
        add(controlPanel, BorderLayout.SOUTH)
        add(statusLabel, BorderLayout.NORTH)

        setupListeners()
    }

    private fun setupListeners() {
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                player?.close()
            }
        })

        playButton.addActionListener {
            player?.play()
            updateStatus("Playing")
        }

        pauseButton.addActionListener {
            player?.pause()
            updateStatus("Paused")
        }

        stopButton.addActionListener {
            player?.stop()
            updateStatus("Stopped")
        }

        volumeSlider.addChangeListener {
            player?.setVolume(volumeSlider.value / 100f)
            updateStatus("Volume: ${volumeSlider.value}%")
        }
    }

    private fun updateStatus(message: String) {
        SwingUtilities.invokeLater {
            statusLabel.text = "Status: $message"
        }
    }

    fun initializePlayer() {
        try {
            updateStatus("Initializing player...")

            val hwnd = HWND(Native.getComponentPointer(videoCanvas))
            player = MFPlayer.create(hwnd.pointer, callback)

            player?.apply {
                setVolume(0.5f)
            }

            loadMedia("C:\\Users\\Elyahou Gambache\\Desktop\\videoplayback.mp4")
            updateStatus("Media loading...")

        } catch (e: Exception) {
            updateStatus("Error: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadMedia(path: String) {
        try {
            updateStatus("Loading media: $path")

            val mediaItemRef = PointerByReference()
            MFPlayerLibrary.INSTANCE.MFPMediaPlayer_CreateMediaItemW(
                player?.getPointer(),
                WString(path),
                0,
                mediaItemRef
            )

            mediaItemRef.value?.let { mediaItem ->
                MFPlayerLibrary.INSTANCE.MFPMediaPlayer_SetMediaItem(
                    player?.getPointer(),
                    mediaItem
                )
            }

        } catch (e: Exception) {
            updateStatus("Error loading media: ${e.message}")
            e.printStackTrace()
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            } catch (e: Exception) {
                e.printStackTrace()
            }

            SwingUtilities.invokeLater {
                MediaPlayerExample().apply {
                    isVisible = true
                    initializePlayer()
                }
            }
        }
    }
}

fun main() {
    MediaPlayerExample.main(arrayOf())
}