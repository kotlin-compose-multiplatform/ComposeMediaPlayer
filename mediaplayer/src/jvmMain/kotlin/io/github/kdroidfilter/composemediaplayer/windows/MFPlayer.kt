package io.github.kdroidfilter.composemediaplayer.windows

import com.sun.jna.Pointer
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.PointerByReference
import java.io.Closeable

/**
 * Classe wrapper pour une utilisation plus simple de MFPlayer
 */
class MFPlayer private constructor(private val player: Pointer) : Closeable {
    companion object {
        fun create(hwnd: Pointer, callback: MFPlayerLibrary.IMFPCallback): MFPlayer {
            val playerRef = PointerByReference()
            if (!MFPlayerLibrary.INSTANCE.MFPMediaPlayer_Init(hwnd, callback, playerRef)) {
                throw MFPlayerException("Failed to initialize MFPlayer")
            }
            return MFPlayer(playerRef.value)
        }
    }

    fun play() = checkOperation { MFPlayerLibrary.INSTANCE.MFPMediaPlayer_Play(player) }
    fun pause() = checkOperation { MFPlayerLibrary.INSTANCE.MFPMediaPlayer_Pause(player) }
    fun stop() = checkOperation { MFPlayerLibrary.INSTANCE.MFPMediaPlayer_Stop(player) }

    fun setVolume(volume: Float) = checkOperation {
        MFPlayerLibrary.INSTANCE.MFPMediaPlayer_SetVolume(player, volume.coerceIn(0f, 1f))
    }

    fun getVolume(): Float {
        val volume = FloatByReference()
        checkOperation { MFPlayerLibrary.INSTANCE.MFPMediaPlayer_GetVolume(player, volume) }
        return volume.value
    }

    fun getNativeVideoSize(): Pair<MFPlayerLibrary.SIZE, MFPlayerLibrary.SIZE> {
        val videoSize = MFPlayerLibrary.SIZE.ByReference()
        val arSize = MFPlayerLibrary.SIZE.ByReference()
        checkOperation {
            MFPlayerLibrary.INSTANCE.MFPMediaPlayer_GetNativeVideoSize(player, videoSize, arSize)
        }
        return Pair(videoSize, arSize)
    }

    fun getPointer(): Pointer {
        return player
    }

    override fun close() {
        MFPlayerLibrary.INSTANCE.MFPMediaPlayer_Free(PointerByReference(player))
    }

    private inline fun checkOperation(operation: () -> Boolean) {
        if (!operation()) {
            throw MFPlayerException("Operation failed")
        }
    }
}