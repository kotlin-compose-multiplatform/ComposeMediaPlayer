@file:Suppress("unused")

package io.github.kdroidfilter.composemediaplayer

import kotlin.js.JsAny
import org.w3c.dom.HTMLMediaElement
import org.khronos.webgl.Uint8Array

/**
 * Represents the main audio context
 */
external class AudioContext : JsAny {
    constructor()
    val destination: AudioDestinationNode
    fun createMediaElementSource(mediaElement: HTMLMediaElement): MediaElementAudioSourceNode
    fun createChannelSplitter(numberOfOutputs: Int = definedExternally): ChannelSplitterNode
    fun createAnalyser(): AnalyserNode
    val state: String
    fun resume()
    fun close()
}

/**
 * Represents a generic node in the Web Audio API
 */
external open class AudioNode : JsAny {
    fun connect(destination: AudioNode, output: Int = definedExternally, input: Int = definedExternally): AudioNode
    fun disconnect()
}

/**
 * Context output node
 */
external class AudioDestinationNode : AudioNode

/**
 * Audio source based on a media element (audio/video)
 */
external class MediaElementAudioSourceNode : AudioNode

/**
 * Allows splitting into multiple channels
 */
external class ChannelSplitterNode : AudioNode

/**
 * Node for analyzing spectrum/audio data
 */
external class AnalyserNode : AudioNode {
    var fftSize: Int
    val frequencyBinCount: Int
    fun getByteFrequencyData(array: Uint8Array)
}