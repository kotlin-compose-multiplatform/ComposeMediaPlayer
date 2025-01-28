package io.github.kdroidfilter.composemediaplayer

import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLVideoElement

class VideoAudioAnalyzer(private val video: HTMLVideoElement) {

    private var audioContext: AudioContext? = null
    private var sourceNode: MediaElementAudioSourceNode? = null
    private var splitterNode: ChannelSplitterNode? = null

    private var leftAnalyser: AnalyserNode? = null
    private var rightAnalyser: AnalyserNode? = null

    private var leftData: Uint8Array? = null
    private var rightData: Uint8Array? = null

    /**
     * Initializes Web Audio (creates a source, a splitter, etc.)
     * In case of error (CORS), we simply return => the video remains managed by HTML
     */
    fun initialize() {
        if (audioContext != null) return // already initialized?

        val ctx = AudioContext()
        audioContext = ctx

        val source = try {
            ctx.createMediaElementSource(video)
        } catch (e: Throwable) {
            println("AudioAnalyzer => CORS/format error => fallback => ${e.message}")
            return
        }

        sourceNode = source
        splitterNode = ctx.createChannelSplitter(2)

        leftAnalyser = ctx.createAnalyser().apply { fftSize = 256 }
        rightAnalyser = ctx.createAnalyser().apply { fftSize = 256 }

        // Chaining
        source.connect(splitterNode!!)
        splitterNode!!.connect(leftAnalyser!!, 0, 0)
        splitterNode!!.connect(rightAnalyser!!, 1, 0)

        // To hear the sound via Web Audio
        splitterNode!!.connect(ctx.destination)

        val size = leftAnalyser!!.frequencyBinCount
        leftData = Uint8Array(size)
        rightData = Uint8Array(size)

        println("AudioAnalyzer => OK => Web Audio capturing audio.")
    }

    /**
     * Returns (left%, right%) in range 0..100
     */
    fun getAudioLevels(): Pair<Float, Float> {
        val la = leftAnalyser ?: return 0f to 0f
        val ra = rightAnalyser ?: return 0f to 0f
        val lb = leftData ?: return 0f to 0f
        val rb = rightData ?: return 0f to 0f

        la.getByteFrequencyData(lb)
        ra.getByteFrequencyData(rb)

        var sumLeft = 0
        for (i in 0 until lb.length) {
            sumLeft += lb[i].toInt()
        }
        var sumRight = 0
        for (i in 0 until rb.length) {
            sumRight += rb[i].toInt()
        }

        val avgLeft = sumLeft.toFloat() / lb.length
        val avgRight = sumRight.toFloat() / rb.length

        val leftPercent = ((avgLeft / 255f) * 100f).coerceAtMost(100f)
        val rightPercent = ((avgRight / 255f) * 100f).coerceAtMost(100f)

        return leftPercent to rightPercent
    }
}