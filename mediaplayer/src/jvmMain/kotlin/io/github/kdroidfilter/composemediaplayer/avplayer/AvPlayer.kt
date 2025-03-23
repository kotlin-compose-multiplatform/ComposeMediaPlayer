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
 * JNA Interface to the native library.
 */
internal interface SharedVideoPlayer : Library {
    fun createVideoPlayer(): Pointer?
    fun openUri(context: Pointer?, uri: String?)
    fun playVideo(context: Pointer?)
    fun pauseVideo(context: Pointer?)
    fun getLatestFrame(context: Pointer?): Pointer?
    fun getFrameWidth(context: Pointer?): Int
    fun getFrameHeight(context: Pointer?): Int
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
    private var timer: Timer? = null

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
        timer?.stop()
        disposePlayer()
        super.removeNotify()
    }

    private fun initPlayer() {
        println("Initializing native VideoPlayer with shared buffer...")
        playerPtr = SharedVideoPlayer.INSTANCE.createVideoPlayer()
        if (playerPtr != null) {
            timer = Timer(16) { updateFrame() }
            timer?.start()
        } else {
            System.err.println("Failed to create native video player.")
        }
    }

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

    fun openMedia(uri: String) {
        if (playerPtr == null) {
            initPlayer()
            SwingUtilities.invokeLater { openMedia(uri) }
            return
        }
        println("Opening media: $uri")
        SharedVideoPlayer.INSTANCE.openUri(playerPtr, uri)
    }

    fun play() {
        playerPtr?.let { SharedVideoPlayer.INSTANCE.playVideo(it) }
    }

    fun pause() {
        playerPtr?.let { SharedVideoPlayer.INSTANCE.pauseVideo(it) }
    }

    private fun disposePlayer() {
        playerPtr?.let {
            SharedVideoPlayer.INSTANCE.disposeVideoPlayer(it)
            playerPtr = null
            println("Native video player released.")
        }
    }
}

