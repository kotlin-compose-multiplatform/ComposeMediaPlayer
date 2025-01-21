package io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.wrapper

import com.sun.jna.ptr.LongByReference
import io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo.MediaPlayerLib

class MediaPlayerSlider(private val mediaPlayer: MediaPlayerLib) {
    /**
     * Gets the total duration of the media in seconds
     * @return The duration in seconds, or null if an error occurs
     */
    fun getDurationInSeconds(): Double? {
        val duration = LongByReference()
        return if (mediaPlayer.GetDuration(duration) == 0) { // S_OK = 0
            MediaPlayerLib.hundredNanoToSeconds(duration.value)
        } else null
    }

    /**
     * Gets the current playback position in seconds
     * @return The position in seconds, or null if an error occurs
     */
    fun getCurrentPositionInSeconds(): Double? {
        val position = LongByReference()
        return if (mediaPlayer.GetCurrentPosition(position) == 0) {
            MediaPlayerLib.hundredNanoToSeconds(position.value)
        } else null
    }

    /**
     * Sets the playback position in seconds
     * @param seconds The new position in seconds
     * @return true if the position was set successfully, false otherwise
     */
    fun setPositionInSeconds(seconds: Double): Boolean {
        val position = MediaPlayerLib.secondsToHundredNano(seconds)
        return mediaPlayer.SetPosition(position) == 0
    }

    /**
     * Gets the current progress percentage
     * @return The percentage between 0.0 and 1.0, or null if an error occurs
     */
    fun getProgress(): Float? {
        val duration = getDurationInSeconds() ?: return null
        val position = getCurrentPositionInSeconds() ?: return null
        return (position / duration).toFloat()
    }

    /**
     * Sets the position by percentage
     * @param progress The percentage between 0.0 and 1.0
     * @return true if the position was set successfully, false otherwise
     */
    fun setProgress(progress: Float): Boolean {
        if (progress !in 0f..1f) return false

        val duration = getDurationInSeconds() ?: return false
        return setPositionInSeconds(duration * progress)
    }
}