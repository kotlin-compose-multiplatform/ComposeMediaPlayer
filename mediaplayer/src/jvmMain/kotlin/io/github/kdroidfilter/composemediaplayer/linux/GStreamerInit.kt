package io.github.kdroidfilter.composemediaplayer.linux

import com.sun.jna.Platform
import com.sun.jna.platform.win32.Kernel32
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.Version
import java.io.File

/**
 * GStreamerInit is a singleton object responsible for initializing the GStreamer library.
 *
 * This object ensures that the GStreamer library is initialized only once during the application's
 * execution. The initialization process configures the library with the base version and a specific
 * application name.
 *
 * It guarantees that GStreamer is properly prepared before any media-related operations,
 * particularly with components such as media players.
 *
 * Features:
 * - Tracks the initialization state to prevent duplicate initializations.
 * - Configures GStreamer with a specified version and application identifier.
 * - Allows the user to set custom paths to the GStreamer libraries for Windows and macOS.
 */
object GStreamerInit {
    private var initialized = false
    private var userGstPathWindows: String? = null
    private var userGstPathMac: String? = null

    /**
     * Allows the user to set a custom path for GStreamer on Windows.
     *
     * @param path Path to the GStreamer bin directory.
     */
    fun setGStreamerPathWindows(path: String) {
        userGstPathWindows = path
    }

    /**
     * Allows the user to set a custom path for GStreamer on macOS.
     *
     * @param path Path to the GStreamer Libraries directory.
     */
    fun setGStreamerPathMac(path: String) {
        userGstPathMac = path
    }

    /**
     * Initializes GStreamer if it hasn't been initialized already.
     */
    fun init() {
        if (!initialized) {
            configurePaths()
            Gst.init(Version.BASELINE, "ComposeGStreamerPlayer")
            initialized = true
        }
    }

    /**
     * Configures the paths to the GStreamer libraries.
     * On Windows, uses the specified environment variables or default paths.
     * On macOS, adds the path to jna.library.path or uses default values.
     * On Linux, assumes that GStreamer is already in the PATH.
     */
    private fun configurePaths() {
        when {
            Platform.isWindows() -> {
                val gstPath = userGstPathWindows ?: System.getProperty("gstreamer.path", "C:\\gstreamer\\1.0\\msvc_x86_64\\bin")
                if (gstPath.isNotEmpty()) {
                    val systemPath = System.getenv("PATH")
                    if (systemPath.isNullOrBlank()) {
                        Kernel32.INSTANCE.SetEnvironmentVariable("PATH", gstPath)
                    } else {
                        Kernel32.INSTANCE.SetEnvironmentVariable(
                            "PATH", "$gstPath${File.pathSeparator}$systemPath"
                        )
                    }
                }
            }

            Platform.isMac() -> {
                val gstPath = userGstPathMac ?: System.getProperty("gstreamer.path", "/Library/Frameworks/GStreamer.framework/Libraries/")
                if (gstPath.isNotEmpty()) {
                    val jnaPath = System.getProperty("jna.library.path", "").trim()
                    if (jnaPath.isEmpty()) {
                        System.setProperty("jna.library.path", gstPath)
                    } else {
                        System.setProperty("jna.library.path", "$jnaPath${File.pathSeparator}$gstPath")
                    }
                }
            }
            // For Linux, no action required if GStreamer is already in the PATH
        }
    }
}
