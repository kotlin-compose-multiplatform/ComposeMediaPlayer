package io.github.kdroidfilter.composemediaplayer.linux

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kdroidfilter.composemediaplayer.PlatformVideoPlayerState
import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.ElementFactory
import org.freedesktop.gstreamer.Format
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.elements.PlayBin
import org.freedesktop.gstreamer.event.SeekFlags
import org.freedesktop.gstreamer.swing.GstVideoComponent
import java.awt.EventQueue
import java.io.File
import java.net.URI
import java.util.*
import javax.swing.Timer
import kotlin.math.pow

@Stable
class LinuxVideoPlayerState : PlatformVideoPlayerState {

    init {
        GStreamerInit.init()
    }

    private val playbin = PlayBin("playbin")
    val gstVideoComponent = GstVideoComponent()

    private val sliderTimer = Timer(50, null)

    // region: Declaration of @Composable states

    private var _sliderPos by mutableStateOf(0f)
    override var sliderPos: Float
        get() = _sliderPos
        set(value) { _sliderPos = value }

    private var _userDragging by mutableStateOf(false)
    override var userDragging: Boolean
        get() = _userDragging
        set(value) { _userDragging = value }

    private var _loop by mutableStateOf(false)
    override var loop: Boolean
        get() = _loop
        set(value) { _loop = value }

    private var _volume by mutableStateOf(1f)
    override var volume: Float
        get() = _volume
        set(value) {
            _volume = value.coerceIn(0f..1f)
            playbin.set("volume", _volume.toDouble())
        }

    private var _leftLevel by mutableStateOf(0f)
    override val leftLevel: Float
        get() = _leftLevel

    private var _rightLevel by mutableStateOf(0f)
    override val rightLevel: Float
        get() = _rightLevel

    private var _positionText by mutableStateOf("0:00")
    override val positionText: String
        get() = _positionText

    private var _durationText by mutableStateOf("0:00")
    override val durationText: String
        get() = _durationText

    // endregion

    init {
        // GStreamer configuration: level, bus, etc.
        val levelElement = ElementFactory.make("level", "level")
        playbin.set("audio-filter", levelElement)
        playbin.setVideoSink(gstVideoComponent.element)

        // When playback reaches the end
        playbin.bus.connect(Bus.EOS {
            EventQueue.invokeLater {
                if (loop) {
                    playbin.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH), 0)
                } else {
                    stop()
                }
            }
        })

        // Retrieve the "peak" levels
        playbin.bus.connect("element") { _, message ->
            if (message.source == levelElement) {
                val struct = message.structure
                if (struct != null && struct.hasField("peak")) {
                    val peaks = struct.getDoubles("peak")
                    if (peaks.isNotEmpty() && isPlaying) {
                        for (i in peaks.indices) {
                            peaks[i] = 10.0.pow(peaks[i] / 20.0)
                        }
                        val l = if (peaks.isNotEmpty()) peaks[0] else 0.0
                        val r = if (peaks.size > 1) peaks[1] else l
                        EventQueue.invokeLater {
                            _leftLevel = (l.coerceIn(0.0, 1.0) * 100f).toFloat()
                            _rightLevel = (r.coerceIn(0.0, 1.0) * 100f).toFloat()
                        }
                    } else {
                        EventQueue.invokeLater {
                            _leftLevel = 0f
                            _rightLevel = 0f
                        }
                    }
                }
            }
        }

        // Timer to update position/duration
        sliderTimer.addActionListener {
            if (!userDragging) {
                val dur = playbin.queryDuration(Format.TIME)
                val pos = playbin.queryPosition(Format.TIME)
                if (dur > 0) {
                    val relPos = pos.toDouble() / dur.toDouble()
                    _sliderPos = (relPos * 1000.0).toFloat()
                }
                EventQueue.invokeLater {
                    _positionText = formatTimeNs(pos)
                    _durationText = formatTimeNs(dur)
                }
            }
        }
        sliderTimer.start()
    }

    private fun formatTimeNs(ns: Long): String {
        val totalSeconds = ns / 1_000_000_000L
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    override val isPlaying: Boolean
        get() = playbin.isPlaying

    // Common functions

    override fun openUri(uri: String) {
        stop()
        try {
            val uri = if (uri.startsWith("http://") || uri.startsWith("https://")) {
                URI(uri)
            } else {
                // Convert the local file path to a file URI
                File(uri).toURI()
            }
            playbin.setURI(uri)
            play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    override fun play() {
        playbin.play()
        playbin.set("volume", volume.toDouble())
    }

     override fun pause() {
        playbin.pause()
    }

    override fun stop() {
        playbin.stop()
        _sliderPos = 0f
        _positionText = "0:00"
    }

    override fun seekTo(value: Float) {
        val dur = playbin.queryDuration(Format.TIME)
        if (dur > 0) {
            val relPos = value / 1000f
            val seekPos = (relPos * dur.toDouble()).toLong()
            playbin.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH), seekPos)
        }
    }

    override fun dispose() {
        sliderTimer.stop()
        playbin.stop()
        playbin.dispose()
        gstVideoComponent.element.dispose()
        Gst.deinit()
    }
}
