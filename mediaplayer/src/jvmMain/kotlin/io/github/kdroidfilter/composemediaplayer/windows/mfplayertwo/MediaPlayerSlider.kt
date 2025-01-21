package io.github.kdroidfilter.composemediaplayer.windows.mfplayertwo

import com.sun.jna.ptr.LongByReference

class MediaPlayerSlider(private val mediaPlayer: MediaPlayerLib) {
    /**
     * Obtient la durée totale du média en secondes
     * @return La durée en secondes, ou null si une erreur se produit
     */
    fun getDurationInSeconds(): Double? {
        val duration = LongByReference()
        return if (mediaPlayer.GetDuration(duration) == 0) { // S_OK = 0
            MediaPlayerLib.hundredNanoToSeconds(duration.value)
        } else null
    }

    /**
     * Obtient la position actuelle de lecture en secondes
     * @return La position en secondes, ou null si une erreur se produit
     */
    fun getCurrentPositionInSeconds(): Double? {
        val position = LongByReference()
        return if (mediaPlayer.GetCurrentPosition(position) == 0) {
            MediaPlayerLib.hundredNanoToSeconds(position.value)
        } else null
    }

    /**
     * Définit la position de lecture en secondes
     * @param seconds La nouvelle position en secondes
     * @return true si la position a été définie avec succès, false sinon
     */
    fun setPositionInSeconds(seconds: Double): Boolean {
        val position = MediaPlayerLib.secondsToHundredNano(seconds)
        return mediaPlayer.SetPosition(position) == 0
    }

    /**
     * Obtient le pourcentage de progression actuel
     * @return Le pourcentage entre 0.0 et 1.0, ou null si une erreur se produit
     */
    fun getProgress(): Float? {
        val duration = getDurationInSeconds() ?: return null
        val position = getCurrentPositionInSeconds() ?: return null
        return (position / duration).toFloat()
    }

    /**
     * Définit la position par pourcentage
     * @param progress Le pourcentage entre 0.0 et 1.0
     * @return true si la position a été définie avec succès, false sinon
     */
    fun setProgress(progress: Float): Boolean {
        if (progress !in 0f..1f) return false

        val duration = getDurationInSeconds() ?: return false
        return setPositionInSeconds(duration * progress)
    }
}