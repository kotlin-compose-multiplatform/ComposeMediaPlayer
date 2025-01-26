package io.github.kdroidfilter.composemediaplayer

import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLVideoElement
import kotlin.js.Promise

@JsModule("audio-context")
external class AudioContext {
    val destination: AudioNode
    val state: String
    fun createMediaElementSource(element: HTMLVideoElement): MediaElementAudioSourceNode
    fun createAnalyser(): AnalyserNode
    fun createChannelSplitter(channels: Int): ChannelSplitterNode
    fun resume(): Promise<JsAny?>
}

@JsName("AnalyserNode")
external interface AnalyserNode : AudioNode {
    var fftSize: Int
    val frequencyBinCount: Int
    fun getByteFrequencyData(array: Uint8Array)
}

@JsName("MediaElementAudioSourceNode")
external interface MediaElementAudioSourceNode : AudioNode

@JsName("ChannelSplitterNode")
external interface ChannelSplitterNode : AudioNode {
    fun connect(destinationNode: AudioNode, output: Int = definedExternally)
}

external interface AudioNode {
    fun connect(destinationNode: AudioNode): AudioNode
}

class VideoAudioAnalyzer(private val video: HTMLVideoElement) {
    private var audioContext: AudioContext? = null
    private var source: MediaElementAudioSourceNode? = null
    private var analyserLeft: AnalyserNode? = null
    private var analyserRight: AnalyserNode? = null
    private var dataArrayLeft: Uint8Array? = null
    private var dataArrayRight: Uint8Array? = null
    private var isInitialized = false

    suspend fun initialize() {
        if (isInitialized) return

        try {
            audioContext = AudioContext()

            // Vérifier et reprendre le contexte audio si nécessaire
            if (audioContext?.state == "suspended") {
                audioContext?.resume()?.await<JsAny?>()
            }

            source = audioContext?.createMediaElementSource(video)
            val splitter = audioContext?.createChannelSplitter(2)

            analyserLeft = audioContext?.createAnalyser()?.apply {
                fftSize = 256
            }

            analyserRight = audioContext?.createAnalyser()?.apply {
                fftSize = 256
            }

            // Connecter les éléments avec une gestion plus sûre des nullables
            source?.let { src ->
                splitter?.let { split ->
                    src.connect(split)

                    analyserLeft?.let { left ->
                        split.connect(left, 0)
                        left.connect(audioContext!!.destination)
                    }

                    analyserRight?.let { right ->
                        split.connect(right, 1)
                        right.connect(audioContext!!.destination)
                    }
                }
            }

            // Initialiser les tableaux de données
            analyserLeft?.let { left ->
                dataArrayLeft = Uint8Array(left.frequencyBinCount)
            }
            analyserRight?.let { right ->
                dataArrayRight = Uint8Array(right.frequencyBinCount)
            }

            isInitialized = true
        } catch (e: Exception) {
            dispose()
        }
    }

    fun getAudioLevels(): Pair<Float, Float> {
        if (!isInitialized) return Pair(0f, 0f)

        try {
            dataArrayLeft?.let { left ->
                analyserLeft?.getByteFrequencyData(left)
            }
            dataArrayRight?.let { right ->
                analyserRight?.getByteFrequencyData(right)
            }

            val leftLevel = dataArrayLeft?.let { calculateAverageLevel(it) } ?: 0f
            val rightLevel = dataArrayRight?.let { calculateAverageLevel(it) } ?: 0f

            return Pair(leftLevel, rightLevel)
        } catch (e: Exception) {
            return Pair(0f, 0f)
        }
    }

    private fun calculateAverageLevel(dataArray: Uint8Array): Float {
        // Get length via WebGL binding property
        val length = dataArray.length
        if (length == 0) return 0f

        var sum = 0.0

        // Type-safe element access with explicit conversion
        for (i in 0 until length) {
            sum += dataArray.get(i).toDouble()
        }

        val average = sum / length
        return (average / 255.0).toFloat()
    }

    fun dispose() {
        try {
            source?.connect(audioContext?.destination ?: return)
            audioContext = null
            source = null
            analyserLeft = null
            analyserRight = null
            dataArrayLeft = null
            dataArrayRight = null
            isInitialized = false
        } catch (e: Exception) {
        }
    }
}