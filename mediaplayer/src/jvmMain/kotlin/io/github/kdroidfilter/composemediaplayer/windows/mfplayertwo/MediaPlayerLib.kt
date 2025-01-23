package io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.BOOLByReference
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.win32.StdCallLibrary

interface MediaPlayerLib : StdCallLibrary {
    companion object {
        // Events constants
        const val MP_EVENT_MEDIAITEM_CREATED   = 1
        const val MP_EVENT_MEDIAITEM_SET       = 2
        const val MP_EVENT_PLAYBACK_STARTED    = 3
        const val MP_EVENT_PLAYBACK_STOPPED    = 4
        const val MP_EVENT_PLAYBACK_ERROR      = 5
        const val MP_EVENT_PLAYBACK_PAUSED     = 6
        const val MP_EVENT_LOADING_STARTED     = 7
        const val MP_EVENT_LOADING_COMPLETE    = 8
        const val MP_EVENT_PLAYBACK_ENDED      = 9

        // Singleton instance
        val INSTANCE: MediaPlayerLib by lazy {
            try {
                Native.load("simpleplayer", MediaPlayerLib::class.java).also {
                    println("MediaPlayerLib loaded successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException("Failed to load simpleplayer library", e)
            }
        }

        // Time conversion utilities
        fun hundredNanoToSeconds(hundredNanoTime: Long): Double = hundredNanoTime / 10000000.0
        fun secondsToHundredNano(seconds: Double): Long = (seconds * 10000000.0).toLong()
    }

    // Callback interface
    fun interface MediaPlayerCallback : Callback {
        fun invoke(eventType: Int, hr: Int)
    }

    // Initialization and cleanup
    fun InitializeMediaPlayer(hwnd: WinDef.HWND, callback: MediaPlayerCallback): Int
    fun CleanupMediaPlayer()

    // Playback control
    fun PlayFile(filePath: WString): Int
    fun PlayURL(url: WString): Int
    fun PausePlayback(): Int
    fun ResumePlayback(): Int
    fun StopPlayback(): Int
    fun UpdateVideo()

    // Player state
    fun IsInitialized(): Boolean
    fun HasVideo(): Boolean
    fun IsLoading(): Boolean
    fun IsPlaying(): Boolean

    // Audio control
    fun SetVolume(level: Float): Int
    fun GetVolume(pLevel: FloatByReference): Int
    fun SetMute(bMute: Boolean): Int
    fun GetMute(pbMute: BOOLByReference): Int
    fun GetChannelLevels(
        pLeft: FloatByReference,
        pRight: FloatByReference
    ): Int

    // Position and duration control
    fun GetDuration(pDuration: LongByReference): Int
    fun GetCurrentPosition(pPosition: LongByReference): Int
    fun SetPosition(position: Long): Int

}