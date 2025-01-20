package io.github.kdroidfilter.composemediaplayer.windows

/**
 * Constants pour MFPlayer
 */
object MFPConstants {
    // Player States
    const val MFP_MEDIAPLAYER_STATE_EMPTY = 0
    const val MFP_MEDIAPLAYER_STATE_STOPPED = 1
    const val MFP_MEDIAPLAYER_STATE_PLAYING = 2
    const val MFP_MEDIAPLAYER_STATE_PAUSED = 3
    const val MFP_MEDIAPLAYER_STATE_SHUTDOWN = 4

    // Options
    const val MFP_OPTION_NONE = 0
    const val MFP_OPTION_FREE_THREADED_CALLBACK = 0x1
    const val MFP_OPTION_NO_MMCSS = 0x2
    const val MFP_OPTION_NO_REMOTE_DESKTOP_OPTIMIZATION = 0x4

    // Window Settings
    const val DEFAULT_WINDOW_WIDTH = 800
    const val DEFAULT_WINDOW_HEIGHT = 600
    const val MFP_ASPECT_RATIO_PRESERVE = 1
    const val MFP_VIDEO_BORDER_COLOR = 0x00000000 // Black
}