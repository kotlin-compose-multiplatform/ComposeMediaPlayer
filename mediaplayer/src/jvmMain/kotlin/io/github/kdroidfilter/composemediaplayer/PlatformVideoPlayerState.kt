package io.github.kdroidfilter.composemediaplayer

interface PlatformVideoPlayerState {
    val isPlaying: Boolean
    var volume: Float
    var sliderPos: Float
    var userDragging: Boolean
    var loop: Boolean
    val leftLevel: Float
    val rightLevel: Float
    val positionText: String
    val durationText: String

    fun openUri(uri: String)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(value: Float)
    fun dispose()
}