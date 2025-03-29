package io.github.kdroidfilter.composemediaplayer.linux

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import io.github.kdroidfilter.composemediaplayer.PlatformVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.SubtitleTrack
import io.github.kdroidfilter.composemediaplayer.VideoMetadata
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.util.DEFAULT_ASPECT_RATIO
import io.github.kdroidfilter.composemediaplayer.util.formatTime
import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Buffer
import org.freedesktop.gstreamer.Caps
import org.freedesktop.gstreamer.Element
import org.freedesktop.gstreamer.ElementFactory
import org.freedesktop.gstreamer.Format
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.GstObject
import org.freedesktop.gstreamer.FlowReturn
import org.freedesktop.gstreamer.Sample
import org.freedesktop.gstreamer.State.PAUSED
import org.freedesktop.gstreamer.State.PLAYING
import org.freedesktop.gstreamer.State.READY
import org.freedesktop.gstreamer.Structure
import org.freedesktop.gstreamer.TagList
import org.freedesktop.gstreamer.elements.AppSink
import org.freedesktop.gstreamer.elements.PlayBin
import org.freedesktop.gstreamer.event.SeekFlags
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.awt.EventQueue
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.util.*
import javax.swing.Timer
import kotlin.math.pow

/**
 * LinuxVideoPlayerState serves as a Linux-specific implementation of a
 * video player state, utilizing GStreamer.
 *
 * In this version, to dynamically change the subtitle source, the pipeline
 * is reset to READY and then PLAYING, and a Timer triggers a seek with
 * a slight delay to resume playback exactly at the saved position.
 *
 * For local subtitles, the path is converted into a file URI.
 *
 * Note: This approach may cause a slight glitch when changing subtitles.
 *
 */
@Stable
class LinuxVideoPlayerState : PlatformVideoPlayerState {

    companion object {
        // Flag to enable text subtitles (GST_PLAY_FLAG_TEXT)
        const val GST_PLAY_FLAG_TEXT = 1 shl 2
    }

    init {
        GStreamerInit.init()
    }

    private val playbin = PlayBin("playbin")
    private val videoSink = ElementFactory.make("appsink", "videosink") as AppSink
    private val sliderTimer = Timer(50, null)

    // Video frame properties
    private var _currentFrame by mutableStateOf<ImageBitmap?>(null)
    val currentFrame: ImageBitmap?
        get() = _currentFrame

    private var frameWidth = 0
    private var frameHeight = 0

    // region: State Declarations
    private var bufferingPercent by mutableStateOf(100)
    private var isUserPaused by mutableStateOf(false)

    private var _sliderPos by mutableStateOf(0f)
    override var sliderPos: Float
        get() = _sliderPos
        set(value) {
            _sliderPos = value
        }

    private var targetSeekPos: Float = 0f

    private var _userDragging by mutableStateOf(false)
    override var userDragging: Boolean
        get() = _userDragging
        set(value) {
            _userDragging = value
        }

    private var _loop by mutableStateOf(false)
    override var loop: Boolean
        get() = _loop
        set(value) {
            _loop = value
        }

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

    private var _isLoading by mutableStateOf(false)
    override val isLoading: Boolean
        get() = _isLoading

    private var _hasMedia by mutableStateOf(false)
    override val hasMedia: Boolean
        get() = _hasMedia

    override fun showMedia() {
        _hasMedia = true
    }

    override fun hideMedia() {
        _hasMedia = false
    }

    private var _isPlaying by mutableStateOf(false)
    override val isPlaying: Boolean
        get() = _isPlaying

    private var _error by mutableStateOf<VideoPlayerError?>(null)
    override val error: VideoPlayerError?
        get() = _error
    override val metadata: VideoMetadata = VideoMetadata()
    override var subtitlesEnabled: Boolean = false
    override var currentSubtitleTrack: SubtitleTrack? = null
    override val availableSubtitleTracks = mutableListOf<SubtitleTrack>()
    // endregion

    /**
     * Selects the subtitle track to use.
     *
     * To change subtitles on the fly, if the video is playing, we save the
     * current position, force the pipeline to READY state, update the source
     * and the flag, then switch back to PLAYING. A Timer then triggers a seek
     * to the saved position.
     *
     * For local subtitles, the path is converted to a file URI.
     */
    override fun selectSubtitleTrack(track: SubtitleTrack?) {
        currentSubtitleTrack = track
        try {
            if (track != null) {
                val suburi = if (track.src.toString().startsWith("http://") ||
                    track.src.toString().startsWith("https://")
                ) {
                    track.src.toString()
                } else {
                    File(track.src.toString()).toURI().toString()
                }
                if (isPlaying) {
                    val pos = playbin.queryPosition(Format.TIME)
                    // Force a complete pipeline reconfiguration
                    playbin.state = READY
                    playbin.set("suburi", suburi)
                    val currentFlags = playbin.get("flags") as Int
                    playbin.set("flags", currentFlags or GST_PLAY_FLAG_TEXT)
                    playbin.state = PLAYING
                    // Use a Timer to delay the seek and resume at the correct position
                    Timer(100) { _ ->
                        playbin.seekSimple(
                            Format.TIME,
                            EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                            pos
                        )
                    }.apply {
                        isRepeats = false
                        start()
                    }
                } else {
                    playbin.set("suburi", suburi)
                    val currentFlags = playbin.get("flags") as Int
                    playbin.set("flags", currentFlags or GST_PLAY_FLAG_TEXT)
                }
                subtitlesEnabled = true
            } else {
                disableSubtitles()
            }
        } catch (e: Exception) {
            _error = VideoPlayerError.UnknownError("Error selecting subtitles: ${e.message}")
        }
    }

    /**
     * Selects the subtitle track to use.
     *
     * To dynamically change subtitles, if the video is playing, we save the
     * current position, force the pipeline to READY state, update the source
     * and flag, and then resume PLAYING. A Timer then triggers a seek to
     * resume playback at the saved position.
     *
     * For local subtitles, the path is converted into a file URI.
     */
    override fun disableSubtitles() {
        currentSubtitleTrack = null
        try {
            if (isPlaying) {
                val pos = playbin.queryPosition(Format.TIME)
                playbin.state = READY
                playbin.set("suburi", "")
                val currentFlags = playbin.get("flags") as Int
                playbin.set("flags", currentFlags and GST_PLAY_FLAG_TEXT.inv())
                playbin.state = PLAYING
                Timer(100) { _ ->
                    playbin.seekSimple(
                        Format.TIME,
                        EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE),
                        pos
                    )
                }.apply {
                    isRepeats = false
                    start()
                }
            } else {
                playbin.set("suburi", "")
                val currentFlags = playbin.get("flags") as Int
                playbin.set("flags", currentFlags and GST_PLAY_FLAG_TEXT.inv())
            }
            subtitlesEnabled = false
        } catch (e: Exception) {
            _error = VideoPlayerError.UnknownError("Error disabling subtitles : ${e.message}")
        }
    }

    private var lastAspectRatioUpdateTime: Long = 0
    private val ASPECT_RATIO_DEBOUNCE_MS = 500
    private var _aspectRatio by mutableStateOf(DEFAULT_ASPECT_RATIO)
    val aspectRatio: Float
        get() = _aspectRatio

    init {
        // GStreamer Configuration
        val levelElement = ElementFactory.make("level", "level")
        playbin.set("audio-filter", levelElement)

        // Configure AppSink for video
        // We request RGBA format and use the raw data directly without any color conversion
        // This approach preserves the original color representation from GStreamer
        val caps = Caps.fromString("video/x-raw,format=RGBA")
        videoSink.setCaps(caps)
        videoSink.set("emit-signals", true)
        videoSink.connect(object : AppSink.NEW_SAMPLE {
            override fun newSample(appSink: AppSink): FlowReturn {
                val sample = appSink.pullSample()
                if (sample != null) {
                    processSample(sample)
                    sample.dispose()
                }
                return FlowReturn.OK
            }
        })

        playbin.setVideoSink(videoSink)

        // Bus Event Management
        playbin.bus.connect(object : Bus.EOS {
            override fun endOfStream(source: GstObject) {
                EventQueue.invokeLater {
                    if (loop) {
                        playbin.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH), 0)
                    } else {
                        stop()
                    }
                    _isPlaying = loop // Updates state based on loop mode
                }
            }
        })

        playbin.bus.connect(object : Bus.ERROR {
            override fun errorMessage(source: GstObject, code: Int, message: String) {
                EventQueue.invokeLater {
                    _error = when {
                        message.contains("codec") || message.contains("decode") ->
                            VideoPlayerError.CodecError(message)

                        message.contains("network") || message.contains("connection") ||
                                message.contains("DNS") || message.contains("http") ->
                            VideoPlayerError.NetworkError(message)

                        message.contains("source") || message.contains("uri") ||
                                message.contains("resource") ->
                            VideoPlayerError.SourceError(message)

                        else ->
                            VideoPlayerError.UnknownError(message)
                    }
                    stop()
                }
            }
        })

        playbin.bus.connect(object : Bus.BUFFERING {
            override fun bufferingData(source: GstObject, percent: Int) {
                EventQueue.invokeLater {
                    bufferingPercent = percent
                    updateLoadingState()
                }
            }
        })

        playbin.bus.connect(object : Bus.STATE_CHANGED {
            override fun stateChanged(
                source: GstObject,
                old: org.freedesktop.gstreamer.State,
                current: org.freedesktop.gstreamer.State,
                pending: org.freedesktop.gstreamer.State,
            ) {
                EventQueue.invokeLater {
                    when (current) {
                        PLAYING -> {
                            _isPlaying = true
                            isUserPaused = false
                            updateLoadingState()
                            updateAspectRatio()
                        }

                        PAUSED -> {
                            _isPlaying = false
                            updateLoadingState()
                        }

                        READY -> {
                            _isPlaying = false
                            updateLoadingState()
                        }

                        else -> {
                            _isPlaying = false
                            updateLoadingState()
                        }
                    }
                }
            }
        })

        // TAG management for metadata
        playbin.bus.connect(object : Bus.TAG {
            override fun tagsFound(source: GstObject?, tagList: TagList?) {
                EventQueue.invokeLater {
                    // TODO: Implémenter l'extraction des métadonnées
                }
            }
        })

        // Monitoring audio levels
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

        // Position/duration update timer
        sliderTimer.addActionListener {
            if (!userDragging) {
                val dur = playbin.queryDuration(Format.TIME)
                val pos = playbin.queryPosition(Format.TIME)
                if (dur > 0) {
                    val relPos = pos.toDouble() / dur.toDouble()
                    val currentSliderPos = (relPos * 1000.0).toFloat()

                    if (targetSeekPos > 0f) {
                        if (kotlin.math.abs(targetSeekPos - currentSliderPos) < 1f) {
                            _sliderPos = currentSliderPos
                            targetSeekPos = 0f
                        }
                    } else {
                        _sliderPos = currentSliderPos
                    }

                    if (pos > 0) {
                        EventQueue.invokeLater {
                            _positionText = formatTime(pos, true)
                            _durationText = formatTime(dur, true)
                        }
                    }
                }
            }
        }
        sliderTimer.start()
    }

    private fun updateAspectRatio() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAspectRatioUpdateTime < ASPECT_RATIO_DEBOUNCE_MS) {
            return // Update ignored if too recent
        }
        lastAspectRatioUpdateTime = currentTime

        try {
            val videoSink = playbin.get("video-sink") as? Element
            val sinkPad = videoSink?.getStaticPad("sink")
            val caps = sinkPad?.currentCaps
            val structure = caps?.getStructure(0)

            if (structure != null) {
                val width = structure.getInteger("width")
                val height = structure.getInteger("height")

                if (width > 0 && height > 0) {
                    val calculatedRatio = width.toFloat() / height.toFloat()
                    if (calculatedRatio != _aspectRatio) { // Mise à jour uniquement si la valeur change
                        EventQueue.invokeLater {
                            _aspectRatio = if (calculatedRatio > 0) calculatedRatio else 16f / 9f
                            println("Aspect ratio updated to: $_aspectRatio")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error updating aspect ratio: ${e.message}")
            e.printStackTrace()
            _aspectRatio = 16f / 9f
        }
    }

    private fun updateLoadingState() {
        _isLoading = when {
            bufferingPercent < 100 -> true  // Still buffering
            isUserPaused -> false           // User paused, not loading
            else -> false                   // No buffering or pausing
        }
    }

    override fun openUri(uri: String) {
        stop() // Stop playback and set _isPlaying back to false
        clearError()
        _isLoading = true
        _hasMedia = false

        try {
            val uriObj = if (uri.startsWith("http://") || uri.startsWith("https://")) {
                URI(uri)
            } else {
                File(uri).toURI()
            }
            playbin.setURI(uriObj)
            _hasMedia = true
            play() // Starts playback, which sets _isPlaying to true if successful
        } catch (e: Exception) {
            _error = VideoPlayerError.SourceError("Failed to open URI: ${e.message}")
            _isLoading = false
            _isPlaying = false
            _hasMedia = false
            e.printStackTrace()
        }
    }

    override fun play() {
        try {
            playbin.play()
            playbin.set("volume", volume.toDouble())
            _hasMedia = true
            _isPlaying = true
            isUserPaused = false
            updateLoadingState()
        } catch (e: Exception) {
            _error = VideoPlayerError.UnknownError("Playback failed: ${e.message}")
            _isPlaying = false
        }
    }

    override fun pause() {
        try {
            playbin.pause()
            _isPlaying = false
            isUserPaused = true
            updateLoadingState()
        } catch (e: Exception) {
            _error = VideoPlayerError.UnknownError("Pause failed: ${e.message}")
        }
    }

    override fun stop() {
        playbin.stop()
        _isPlaying = false
        _sliderPos = 0f
        _positionText = "0:00"
        _isLoading = false
        isUserPaused = false
        bufferingPercent = 100
        _hasMedia = false
    }

    override fun seekTo(value: Float) {
        val dur = playbin.queryDuration(Format.TIME)
        if (dur > 0) {
            _sliderPos = value
            targetSeekPos = value

            val relPos = value / 1000f
            val seekPos = (relPos * dur.toDouble()).toLong()
            _positionText = formatTime(seekPos, true)

            playbin.seekSimple(Format.TIME, EnumSet.of(SeekFlags.FLUSH, SeekFlags.ACCURATE), seekPos)

            EventQueue.invokeLater {
                _positionText = formatTime(seekPos, true)
            }
        }
    }

    override fun clearError() {
        _error = null
    }

    /**
     * Processes a video sample from GStreamer and converts it to an ImageBitmap
     * that can be drawn in a Compose Canvas.
     * 
     * This implementation extracts the actual video frame data from the GStreamer Sample
     * and converts it to a Compose ImageBitmap without applying any color conversion.
     * The raw data from GStreamer is passed directly to the Skia Bitmap to preserve
     * the original color representation.
     */
    private fun processSample(sample: Sample) {
        try {
            val caps = sample.caps
            val structure = caps.getStructure(0)

            val width = structure.getInteger("width")
            val height = structure.getInteger("height")

            if (width != frameWidth || height != frameHeight) {
                frameWidth = width
                frameHeight = height
                updateAspectRatio()
                println("Video dimensions: $width x $height")
            }

            // Get the buffer from the sample
            val buffer = sample.buffer
            if (buffer == null) {
                println("Error: Buffer is null")
                return
            }

            // Create a BufferedImage to hold the pixel data
            val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val pixels = IntArray(width * height)

            // Try to get information about the buffer
            println("Attempting to access buffer data")

            // Try to access the buffer data
            try {
                // Get the raw data from the buffer
                val data = buffer.map(false)
                if (data != null) {
                    println("Successfully mapped buffer data")

                    // Copy the data to the pixel array
                    val byteBuffer = data
                    byteBuffer.rewind()

                    // Copy raw data without any color conversion
                    for (i in 0 until width * height) {
                        // Read 4 bytes (RGBA or BGRA) and store them directly
                        val byte1 = byteBuffer.get().toInt() and 0xFF
                        val byte2 = byteBuffer.get().toInt() and 0xFF
                        val byte3 = byteBuffer.get().toInt() and 0xFF
                        val byte4 = byteBuffer.get().toInt() and 0xFF
                        // Store in the same order as received
                        pixels[i] = (byte4 shl 24) or (byte3 shl 16) or (byte2 shl 8) or byte1
                    }

                    // Set the pixels in the BufferedImage
                    bufferedImage.setRGB(0, 0, width, height, pixels, 0, width)

                    // Convert the BufferedImage to a Skia Bitmap
                    val imageInfo = ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL)
                    val bitmap = Bitmap()
                    bitmap.allocPixels(imageInfo)

                    // Copy the pixel data from the BufferedImage to the Skia Bitmap
                    bufferedImage.getRGB(0, 0, width, height, pixels, 0, width)

                    // Set the pixels in the Skia Bitmap
                    val pixmap = bitmap.peekPixels()
                    if (pixmap != null) {
                        for (y in 0 until height) {
                            for (x in 0 until width) {
                                // Use the pixel value directly without any color conversion
                                val pixel = pixels[y * width + x]
                                pixmap.erase(pixel, org.jetbrains.skia.IRect.makeXYWH(x, y, 1, 1))
                            }
                        }
                    }

                    // Convert the Skia Bitmap to a Compose ImageBitmap
                    val imageBitmap = bitmap.asComposeImageBitmap()

                    // Update the current frame on the UI thread
                    EventQueue.invokeLater {
                        _currentFrame = imageBitmap
                    }
                } else {
                    println("Failed to map buffer data")
                }
            } catch (e: Exception) {
                println("Error accessing buffer data: ${e.message}")
                e.printStackTrace()
            }
        } catch (e: Exception) {
            println("Error processing video sample: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun dispose() {
        sliderTimer.stop()
        playbin.stop()
        playbin.dispose()
        videoSink.dispose()
        Gst.deinit()
    }
}
