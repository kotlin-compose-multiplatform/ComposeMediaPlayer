package io.github.kdroidfilter.composemediaplayer

import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Version

/**
 * GStreamerInit is a singleton object responsible for initializing the GStreamer library.
 *
 * This object ensures that the GStreamer library is initialized only once during the runtime
 * of the application. The initialization process sets up the library with the baseline version
 * and a specific application name.
 *
 * It is used as a helper to guarantee that GStreamer is properly prepared before any related
 * media operations are executed, especially with components such as media players.
 *
 * Features:
 * - Tracks the initialization state to prevent duplicate initialization.
 * - Configures GStreamer with a specified version and application identifier.
 */
object GStreamerInit {
    private var initialized = false

    fun init() {
        if (!initialized) {
            Gst.init(Version.BASELINE, "ComposeGStreamerPlayer")
            initialized = true
        }
    }
}
