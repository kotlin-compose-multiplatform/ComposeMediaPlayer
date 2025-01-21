package io.github.kdroidfilter.composemediaplayer.windows.mfplayer2

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary

/**
 * JNA interface for the native MediaPlayer library.
 */
interface MediaPlayerLib2 : StdCallLibrary {
    companion object {
        const val MP_EVENT_MEDIAITEM_CREATED   = 1
        const val MP_EVENT_MEDIAITEM_SET       = 2
        const val MP_EVENT_PLAYBACK_STARTED    = 3
        const val MP_EVENT_PLAYBACK_STOPPED    = 4
        const val MP_EVENT_PLAYBACK_ERROR      = 5

        val INSTANCE: MediaPlayerLib2 by lazy {
            try {
                Native.load("simpleplayer", MediaPlayerLib2::class.java).also {
                    println("MediaPlayerLib loaded successfully")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                throw RuntimeException("Failed to load simpleplayer library", e)
            }
        }
    }

    fun interface MediaPlayerCallback : Callback {
        fun invoke(eventType: Int, hr: Int)
    }

    fun InitializeMediaPlayer(hwnd: WinDef.HWND, callback: MediaPlayerCallback): Int
    fun PlayFile(filePath: WString): Int
    fun PausePlayback(): Int
    fun ResumePlayback(): Int
    fun StopPlayback(): Int
    fun CleanupMediaPlayer()
    fun IsInitialized(): Boolean
    fun HasVideo(): Boolean
    fun UpdateVideo()
}