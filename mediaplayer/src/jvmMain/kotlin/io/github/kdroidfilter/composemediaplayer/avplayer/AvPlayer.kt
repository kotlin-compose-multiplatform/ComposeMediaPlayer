package io.github.kdroidfilter.composemediaplayer.sharedbuffer

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import javax.swing.*

/**
 * JNA interface to the native library.
 */
internal interface SharedVideoPlayer : Library {
    fun createVideoPlayer(): Pointer?
    fun openUri(context: Pointer?, uri: String?)
    fun playVideo(context: Pointer?)
    fun pauseVideo(context: Pointer?)
    fun setVolume(context: Pointer?, volume: Float)
    fun getVolume(context: Pointer?): Float
    fun getLatestFrame(context: Pointer?): Pointer?
    fun getFrameWidth(context: Pointer?): Int
    fun getFrameHeight(context: Pointer?): Int
    fun getVideoDuration(context: Pointer?): Double
    fun getCurrentTime(context: Pointer?): Double
    fun seekTo(context: Pointer?, time: Double)
    fun disposeVideoPlayer(context: Pointer?)

    companion object {
        val INSTANCE: SharedVideoPlayer =
            Native.load("NativeVideoPlayer", SharedVideoPlayer::class.java)
    }
}

/**
 * Swing component that periodically retrieves frames from the shared buffer.
 * Optimized to reduce CPU usage when paused.
 */
class VideoPlayerComponent : JPanel() {
    private var playerPtr: Pointer? = null
    private var bufferedImage: BufferedImage? = null
    private var frameTimer: Timer? = null
    private var isPlaying: Boolean = false

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
     * Starts the frame refresh timer at ~60 fps but only when playing
     */
    private fun startRefreshTimer() {
        stopRefreshTimer()
        isPlaying = true
        frameTimer = Timer(16) { updateFrame() }
        frameTimer?.start()
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