package io.github.kdroidfilter.composemediaplayer.windows.wrapper

import com.sun.jna.ptr.FloatByReference
import io.github.kdroidfilter.composemediaplayer.windows.MediaPlayerLib

class VideoMetrics(private val mediaPlayer: MediaPlayerLib) {

    fun getAspectRatio(): Float? {
        return try {
            val ratio = FloatByReference()
            if (mediaPlayer.GetVideoAspectRatio(ratio) == 0) { // 0 = S_OK
                ratio.value
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}