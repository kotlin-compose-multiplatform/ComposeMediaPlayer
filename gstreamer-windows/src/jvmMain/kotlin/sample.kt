import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.layout.StackPane
import javafx.scene.Scene
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import java.awt.BorderLayout
import java.net.URL
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun main() {
    // Initialize JavaFX Platform
    JFXPanel()

    SwingUtilities.invokeLater {
        createAndShowGUI()
    }
}

private fun createAndShowGUI() {
    val frame = JFrame("Video Player")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.layout = BorderLayout()

    val jfxPanel = JFXPanel()
    frame.add(jfxPanel, BorderLayout.CENTER)
    frame.setSize(1024, 768)

    Platform.runLater {
        initFX(jfxPanel)
    }

    frame.isVisible = true
}

private fun initFX(jfxPanel: JFXPanel) {
    try {
        val videoUrl = "https://rr3---sn-pujapa-ua8l.googlevideo.com/videoplayback?expire=1737312355&ei=A_SMZ_acDd7Op-oP3J3Q-Ak&ip=85.130.160.209&id=o-AP2wdgG2wD3UKn7jjsj9hhatx20td2jCuojvIf_Si5Fy&itag=134&source=youtube&requiressl=yes&xpc=EgVo2aDSNQ%3D%3D&met=1737290755%2C&mh=Bh&mm=31%2C29&mn=sn-pujapa-ua8l%2Csn-ua87sn76&ms=au%2Crdu&mv=m&mvi=3&pcm2cms=yes&pl=22&rms=au%2Cau&initcwndbps=3067500&bui=AY2Et-PxC3nN5YSKVdMGXvpCdalX1htR_m5PFOYqlFNlWQsjb1HaM5oVMQ2bZY1tH2RuX10seSkT8aPp&spc=9kzgDf96FQLksFh_pOZcCZq7t9T9YNnCnBzBDKTmtjplDNh0u-PrBm6PMBxG&vprv=1&svpuc=1&mime=video%2Fmp4&rqh=1&gir=yes&clen=5309187&dur=117.066&lmt=1729075644088371&mt=1737290235&fvip=4&keepalive=yes&fexp=51326932%2C51335594%2C51353498%2C51371294%2C51384461&c=IOS&txp=6309224&sparams=expire%2Cei%2Cip%2Cid%2Citag%2Csource%2Crequiressl%2Cxpc%2Cbui%2Cspc%2Cvprv%2Csvpuc%2Cmime%2Crqh%2Cgir%2Cclen%2Cdur%2Clmt&sig=AJfQdSswRQIhAP5Fx2HiHWCsdNSGiaCP97f75FvoJ42G9WohLb3nYgA_AiAlb9YqJbUhXWMRyPp0_IcK-tBnGnXg1MqQV3G3E7feHQ%3D%3D&lsparams=met%2Cmh%2Cmm%2Cmn%2Cms%2Cmv%2Cmvi%2Cpcm2cms%2Cpl%2Crms%2Cinitcwndbps&lsig=AGluJ3MwRAIgHbFFHWd7aaL_Yq5LuRYAqM5-12PnOe5UM69Rb77EP0ACIEJ5MWmSJNZ28uTYhh2NWiUdcCjEgwpSE8Q8z4x6wIiu" // Remplacez par votre URL
        val url = URL(videoUrl)

        println("Loading video from: $url")

        // Create a StackPane as root container
        val root = StackPane()
        val scene = Scene(root)

        // Create Media and MediaPlayer using URL
        val media = javafx.scene.media.Media(url.toString())
        val mediaPlayer = MediaPlayer(media)

        // Create and configure MediaView
        val mediaView = MediaView(mediaPlayer)

        // Bind MediaView size to scene size
        mediaView.fitWidthProperty().bind(scene.widthProperty())
        mediaView.fitHeightProperty().bind(scene.heightProperty())
        mediaView.isPreserveRatio = true

        // Add MediaView to root
        root.children.add(mediaView)

        // Add listeners for debugging
        mediaPlayer.setOnReady {
            println("MediaPlayer is ready")
            println("Original video size: ${media.width} x ${media.height}")
            println("MediaView size: ${mediaView.fitWidth} x ${mediaView.fitHeight}")

            // Force layout update
            root.applyCss()
            root.layout()
        }

        mediaPlayer.setOnError {
            println("MediaPlayer Error: ${mediaPlayer.error}")
        }

        media.setOnError {
            println("Media Error: ${media.error}")
        }

        mediaView.setOnError {
            println("MediaView Error: ${mediaView.onError}")
        }

        // Set scene to panel
        jfxPanel.scene = scene

        // Start playback
        mediaPlayer.play()

    } catch (e: Exception) {
        e.printStackTrace()
    }
}
