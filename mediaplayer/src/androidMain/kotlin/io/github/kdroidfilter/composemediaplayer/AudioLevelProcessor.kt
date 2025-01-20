package io.github.kdroidfilter.composemediaplayer

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

class AudioLevelProcessor : BaseAudioProcessor() {
    private var channelCount = 0
    private var sampleRateHz = 0
    private var bytesPerFrame = 0
    private var onAudioLevelUpdate: ((Float, Float) -> Unit)? = null

    fun setOnAudioLevelUpdateListener(listener: (Float, Float) -> Unit) {
        onAudioLevelUpdate = listener
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        channelCount = inputAudioFormat.channelCount
        sampleRateHz = inputAudioFormat.sampleRate
        bytesPerFrame = inputAudioFormat.bytesPerFrame
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return
        }

        var leftSum = 0.0
        var rightSum = 0.0
        var sampleCount = 0

        // Copier le buffer pour ne pas affecter la position originale
        val buffer = inputBuffer.duplicate()

        while (buffer.remaining() >= 2) {
            // Lecture des échantillons 16-bit
            val sample = buffer.short / Short.MAX_VALUE.toFloat()

            if (channelCount >= 2) {
                // Stéréo
                if (sampleCount % 2 == 0) {
                    leftSum += abs(sample.toDouble())
                } else {
                    rightSum += abs(sample.toDouble())
                }
            } else {
                // Mono - même valeur pour les deux canaux
                leftSum += abs(sample.toDouble())
                rightSum += abs(sample.toDouble())
            }
            sampleCount++
        }

        // Calculer RMS et convertir en dB
        val samplesPerChannel = if (channelCount >= 2) sampleCount / 2 else sampleCount
        val leftRms = if (samplesPerChannel > 0) sqrt(leftSum / samplesPerChannel) else 0.0
        val rightRms = if (samplesPerChannel > 0) sqrt(rightSum / samplesPerChannel) else 0.0

        // Convertir en pourcentage (0-100)
        val leftLevel = convertToPercentage(leftRms)
        val rightLevel = convertToPercentage(rightRms)

        onAudioLevelUpdate?.invoke(leftLevel, rightLevel)

        // Passer le buffer original tel quel
        val output = replaceOutputBuffer(inputBuffer.remaining())
        output.put(inputBuffer)
        output.flip()
    }

    private fun convertToPercentage(rms: Double): Float {
        if (rms <= 0) return 0f
        val db = 20 * log10(rms)
        // Convertir de -60dB..0dB à 0..100%
        return ((db + 60) / 60 * 100).toFloat().coerceIn(0f, 100f)
    }

    override fun onReset() {
        onAudioLevelUpdate?.invoke(0f, 0f)
    }
}