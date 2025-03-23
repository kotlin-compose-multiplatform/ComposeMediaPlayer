package io.github.kdroidfilter.composemediaplayer.sharedbuffer

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.awt.*
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.swing.*

/**
 * Interface JNA vers la bibliothèque native gérant le player via buffer partagé.
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
 * Composant Swing qui récupère périodiquement les frames depuis le buffer partagé.
 */
class VideoPlayerComponent : JPanel() {
    private var playerPtr: Pointer? = null
    private var bufferedImage: BufferedImage? = null
    private var timer: Timer? = null

    init {
        background = Color.BLACK
        preferredSize = Dimension(640, 360)
        // Initialisation du player dès l'ajout dans la hiérarchie
        addNotify()
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
        println("Initialisation du VideoPlayer natif avec buffer partagé...")
        playerPtr = SharedVideoPlayer.INSTANCE.createVideoPlayer()
        if (playerPtr != null) {
            // Rafraîchissement à environ 60 FPS (toutes les ~16 ms)
            timer = Timer(16) { updateFrame() }
            timer?.start()
        } else {
            System.err.println("Impossible de créer le player vidéo natif.")
        }
    }

    private fun updateFrame() {
        if (playerPtr == null) return

        // Récupération du pointeur de la dernière frame
        val framePtr = SharedVideoPlayer.INSTANCE.getLatestFrame(playerPtr) ?: return

        // Récupération de la taille
        val width = SharedVideoPlayer.INSTANCE.getFrameWidth(playerPtr)
        val height = SharedVideoPlayer.INSTANCE.getFrameHeight(playerPtr)
        if (width <= 0 || height <= 0) return

        // Créer ou réutiliser la BufferedImage si nécessaire
        if (bufferedImage == null || bufferedImage!!.width != width || bufferedImage!!.height != height) {
            bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        }

        // Lire le contenu ARGB depuis le pointeur natif
        val pixelArray = (bufferedImage!!.raster.dataBuffer as DataBufferInt).data
        val nativePixels = framePtr.getIntArray(0, width * height)
        System.arraycopy(nativePixels, 0, pixelArray, 0, nativePixels.size)

        // Redessiner
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        bufferedImage?.let {
            // Obtenir la largeur du panneau
            val panelWidth = width
            // Calculer le facteur d'échelle pour que l'image occupe toute la largeur
            val scaleFactor = panelWidth.toDouble() / it.width
            // Calculer la nouvelle hauteur en respectant le ratio de l'image
            val scaledHeight = (it.height * scaleFactor).toInt()
            // Centrer verticalement l'image
            val y = (height - scaledHeight) / 2
            // Dessiner l'image redimensionnée
            g.drawImage(it, 0, y, panelWidth, scaledHeight, null)
        }
    }


    fun openMedia(uri: String) {
        if (playerPtr == null) {
            initPlayer()
            SwingUtilities.invokeLater { openMedia(uri) }
            return
        }
        println("Ouverture média : $uri")
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
            println("Player vidéo natif libéré.")
        }
    }
}

/**
 * Exemple d'application Swing pour tester le VideoPlayerComponent.
 */
fun main() {
    SwingUtilities.invokeLater {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val frame = JFrame("Demo VideoPlayer avec buffer partagé")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.setSize(800, 600)

        val playerComponent = VideoPlayerComponent()

        val btnOpenURL = JButton("Ouvrir BigBuckBunny en ligne").apply {
            addActionListener {
                val testURL = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
                playerComponent.openMedia(testURL)
            }
        }

        val btnOpenFile = JButton("Ouvrir fichier local").apply {
            addActionListener {
                val chooser = JFileChooser()
                if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    val f = chooser.selectedFile
                    playerComponent.openMedia(f.absolutePath)
                }
            }
        }

        val btnPlay = JButton("Lecture").apply {
            addActionListener { playerComponent.play() }
        }

        val btnPause = JButton("Pause").apply {
            addActionListener { playerComponent.pause() }
        }

        val controlPanel = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
            add(btnOpenURL)
            add(btnOpenFile)
            add(btnPlay)
            add(btnPause)
        }

        frame.layout = BorderLayout()
        frame.add(playerComponent, BorderLayout.CENTER)
        frame.add(controlPanel, BorderLayout.SOUTH)

        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }
}

