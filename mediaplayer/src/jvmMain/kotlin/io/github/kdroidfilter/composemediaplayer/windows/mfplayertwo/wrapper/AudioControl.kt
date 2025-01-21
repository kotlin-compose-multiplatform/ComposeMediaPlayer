package io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.wrapper

import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.FloatByReference
import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.MediaPlayerLib

/**
 * Wrapper class for audio control functions
 */
class AudioControl(private val mediaPlayerLib: MediaPlayerLib) {
    /**
     * Sets the volume level (0.0 to 1.0)
     * @param level Volume level between 0.0 (silence) and 1.0 (full volume)
     * @return true if successful, false otherwise
     */
    fun setVolume(level: Float): Boolean {
        if (level !in 0.0f..1.0f) return false
        return mediaPlayerLib.SetVolume(level) >= 0
    }

    /**
     * Gets the current volume level
     * @return Volume level between 0.0 and 1.0, or null if operation failed
     */
    fun getVolume(): Float? {
        val volumeRef = FloatByReference()
        return if (mediaPlayerLib.GetVolume(volumeRef) >= 0) {
            volumeRef.value
        } else {
            null
        }
    }

    /**
     * Sets the mute state
     * @param mute true to mute, false to unmute
     * @return true if successful, false otherwise
     */
    fun setMute(mute: Boolean): Boolean {
        return mediaPlayerLib.SetMute(mute) >= 0
    }

    /**
     * Gets the current mute state
     * @return Current mute state, or null if operation failed
     */
    fun getMute(): Boolean? {
        val muteRef = WinDef.BOOLByReference()
        return if (mediaPlayerLib.GetMute(muteRef) >= 0) {
            muteRef.value.booleanValue()
        } else {
            null
        }
    }
}