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
import javax.swing.Timer

/**
 * JNA interface to the native library.
 */
internal interface SharedVideoPlayer : Library {
    fun createVideoPlayer(): Pointer?
    fun openUri(context: Pointer?, uri: String?)
    fun playVideo(context: Pointer?)
    fun pauseVideo(context: Pointer?)
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
 */
class VideoPlayerComponent : JPanel() {
    private var playerPtr: Pointer? = null
    private var bufferedImage: BufferedImage? = null
    private var frameTimer: Timer? = null
    // Store the last playback time to avoid unnecessary updates when paused
    private var lastUpdateTime: Double = -1.0

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
        frameTimer?.stop()
        disposePlayer()
        super.removeNotify()
    }

    /**
     * Initializes the native VideoPlayer and starts the frame update timer.
     */
    private fun initPlayer() {
        println("Initializing the native VideoPlayer with shared buffer...")
        playerPtr = SharedVideoPlayer.INSTANCE.createVideoPlayer()
        if (playerPtr != null) {
            // Timer to refresh images (~60 fps)
            frameTimer = Timer(16) { updateFrame() }
            frameTimer?.start()
        } else {
            System.err.println("Failed to create the native video player.")
        }
    }

    /**
     * Updates the current frame if new frame data is available.
     */
    private fun updateFrame() {
        if (playerPtr == null) return

        val width = SharedVideoPlayer.INSTANCE.getFrameWidth(playerPtr)
        val height = SharedVideoPlayer.INSTANCE.getFrameHeight(playerPtr)
        if (width <= 0 || height <= 0) return

        // Optimization: Only update if playback time has changed
        val currentTime = SharedVideoPlayer.INSTANCE.getCurrentTime(playerPtr)
        if (currentTime == lastUpdateTime) return
        lastUpdateTime = currentTime

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

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        bufferedImage?.let {
            val panelWidth = width
            val scaleFactor = panelWidth.toDouble() / it.width
            val scaledHeight = (it.height * scaleFactor).toInt()
            val y = (height - scaledHeight) / 2
            // Use Graphics2D with nearest neighbor interpolation for performance
            val g2d = g as? Graphics2D
            g2d?.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
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
    }

    /**
     * Starts playback.
     */
    fun play() {
        playerPtr?.let { SharedVideoPlayer.INSTANCE.playVideo(it) }
    }

    /**
     * Pauses playback.
     */
    fun pause() {
        playerPtr?.let { SharedVideoPlayer.INSTANCE.pauseVideo(it) }
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
        playerPtr?.let { SharedVideoPlayer.INSTANCE.seekTo(it, time) }
    }

    /**
     * Disposes of the native video player.
     */
    private fun disposePlayer() {
        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.disposeVideoPlayer(it)
            playerPtr = null
            println("Native video player released.")
        }
    }
}
