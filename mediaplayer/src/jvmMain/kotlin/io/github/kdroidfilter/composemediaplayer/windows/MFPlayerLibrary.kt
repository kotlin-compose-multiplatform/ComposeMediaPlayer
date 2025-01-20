package io.github.kdroidfilter.composemediaplayer.windows

import com.sun.jna.*
import com.sun.jna.platform.win32.WinDef.BOOLByReference
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Interface JNA pour la bibliothèque MFPlayer.
 * @author kdroidFilter
 * @since 2025-01-20
 */
interface MFPlayerLibrary : Library {
    companion object {
        @JvmField
        val INSTANCE: MFPlayerLibrary = Native.load("MFPlayer", MFPlayerLibrary::class.java)
    }

    // Structures
    open class SIZE : Structure() {
        @JvmField var cx: Int = 0
        @JvmField var cy: Int = 0

        override fun getFieldOrder() = listOf("cx", "cy")

        class ByReference : SIZE(), Structure.ByReference
    }

    open class MFVideoNormalizedRect : Structure() {
        @JvmField var left: Float = 0.0f
        @JvmField var top: Float = 0.0f
        @JvmField var right: Float = 1.0f
        @JvmField var bottom: Float = 1.0f

        override fun getFieldOrder() = listOf("left", "top", "right", "bottom")

        class ByReference : MFVideoNormalizedRect(), Structure.ByReference
    }

    // Callback interface
    interface IMFPCallback : Callback {
        fun invoke(pEvent: Pointer?): Int
    }

    // MFPMediaPlayer Functions
    fun MFPMediaPlayer_Init(hWnd: Pointer?, callback: IMFPCallback?, mediaPlayer: PointerByReference): Boolean
    fun MFPMediaPlayer_Free(mediaPlayer: PointerByReference): Boolean

    fun MFPMediaPlayer_Play(mediaPlayer: Pointer?): Boolean
    fun MFPMediaPlayer_Pause(mediaPlayer: Pointer?): Boolean
    fun MFPMediaPlayer_Stop(mediaPlayer: Pointer?): Boolean
    fun MFPMediaPlayer_Step(mediaPlayer: Pointer?): Boolean
    fun MFPMediaPlayer_Toggle(mediaPlayer: Pointer?): Boolean

    fun MFPMediaPlayer_ClearMediaItem(mediaPlayer: Pointer?): Boolean
    fun MFPMediaPlayer_SetMediaItem(mediaPlayer: Pointer?, mediaItem: Pointer?): Boolean
    fun MFPMediaPlayer_GetMediaItem(mediaPlayer: Pointer?, mediaItem: PointerByReference): Boolean
    fun MFPMediaPlayer_CreateMediaItemA(mediaPlayer: Pointer?, url: String?, userData: Int, mediaItem: PointerByReference): Boolean
    fun MFPMediaPlayer_CreateMediaItemW(mediaPlayer: Pointer?, url: WString?, userData: Int, mediaItem: PointerByReference): Boolean
    fun MFPMediaPlayer_CreateMediaItemFromObject(mediaPlayer: Pointer?, obj: Pointer?, userData: Int, mediaItem: PointerByReference): Boolean

    fun MFPMediaPlayer_GetState(mediaPlayer: Pointer?, state: IntByReference): Boolean
    fun MFPMediaPlayer_SetPosition(mediaPlayer: Pointer?, position: Long): Boolean
    fun MFPMediaPlayer_GetPosition(mediaPlayer: Pointer?, position: LongByReference): Boolean
    fun MFPMediaPlayer_GetDuration(mediaPlayer: Pointer?, duration: LongByReference): Boolean

    fun MFPMediaPlayer_SetRate(mediaPlayer: Pointer?, rate: Float): Boolean
    fun MFPMediaPlayer_GetRate(mediaPlayer: Pointer?, rate: FloatByReference): Boolean
    fun MFPMediaPlayer_GetSupportedRates(mediaPlayer: Pointer?, forward: Boolean, slowestRate: FloatByReference, fastestRate: FloatByReference): Boolean

    fun MFPMediaPlayer_GetVolume(mediaPlayer: Pointer?, volume: FloatByReference): Boolean
    fun MFPMediaPlayer_SetVolume(mediaPlayer: Pointer?, volume: Float): Boolean
    fun MFPMediaPlayer_GetBalance(mediaPlayer: Pointer?, balance: FloatByReference): Boolean
    fun MFPMediaPlayer_SetBalance(mediaPlayer: Pointer?, balance: Float): Boolean
    fun MFPMediaPlayer_GetMute(mediaPlayer: Pointer?, mute: BOOLByReference): Boolean
    fun MFPMediaPlayer_SetMute(mediaPlayer: Pointer?, mute: Boolean): Boolean

    fun MFPMediaPlayer_GetNativeVideoSize(mediaPlayer: Pointer?, videoSize: SIZE.ByReference, arSize: SIZE.ByReference): Boolean
    fun MFPMediaPlayer_GetIdealVideoSize(mediaPlayer: Pointer?, minSize: SIZE.ByReference, maxSize: SIZE.ByReference): Boolean
    fun MFPMediaPlayer_SetVideoSourceRect(mediaPlayer: Pointer?, rect: MFVideoNormalizedRect): Boolean
    fun MFPMediaPlayer_GetVideoSourceRect(mediaPlayer: Pointer?, rect: MFVideoNormalizedRect): Boolean

    fun MFPMediaPlayer_SetAspectRatioMode(mediaPlayer: Pointer?, mode: Int): Boolean
    fun MFPMediaPlayer_GetAspectRatioMode(mediaPlayer: Pointer?, mode: IntByReference): Boolean

    fun MFPMediaPlayer_GetVideoWindow(mediaPlayer: Pointer?, hwnd: PointerByReference): Boolean
    fun MFPMediaPlayer_UpdateVideo(mediaPlayer: Pointer?): Boolean
    fun MFPMediaPlayer_SetBorderColor(mediaPlayer: Pointer?, color: Int): Boolean
    fun MFPMediaPlayer_GetBorderColor(mediaPlayer: Pointer?, color: IntByReference): Boolean

    // Media Item Functions
    fun MFPMediaItem_Release(mediaItem: Pointer?): Boolean
    fun MFPMediaItem_GetMediaPlayer(mediaItem: Pointer?, mediaPlayer: PointerByReference): Boolean
    fun MFPMediaItem_GetURLA(mediaItem: Pointer?, url: PointerByReference): Boolean
    fun MFPMediaItem_GetURLW(mediaItem: Pointer?, url: PointerByReference): Boolean

    fun MFPMediaItem_SetUserData(mediaItem: Pointer?, userData: Int): Boolean
    fun MFPMediaItem_GetUserData(mediaItem: Pointer?, userData: IntByReference): Boolean

    fun MFPMediaItem_SetStartStopPosition(mediaItem: Pointer?, start: Long, stop: Long): Boolean
    fun MFPMediaItem_GetStartStopPosition(mediaItem: Pointer?, start: LongByReference, stop: LongByReference): Boolean

    fun MFPMediaItem_HasVideo(mediaItem: Pointer?, hasVideo: BOOLByReference, selected: BOOLByReference): Boolean
    fun MFPMediaItem_HasAudio(mediaItem: Pointer?, hasAudio: BOOLByReference, selected: BOOLByReference): Boolean
    fun MFPMediaItem_IsProtected(mediaItem: Pointer?, isProtected: BOOLByReference): Boolean

    fun MFPMediaItem_GetDuration(mediaItem: Pointer?, duration: LongByReference): Boolean
    fun MFPMediaItem_GetNumberOfStreams(mediaItem: Pointer?, streamCount: IntByReference): Boolean
    fun MFPMediaItem_SetStreamSelection(mediaItem: Pointer?, streamIndex: Int, enabled: Boolean): Boolean
    fun MFPMediaItem_GetStreamSelection(mediaItem: Pointer?, streamIndex: Int, enabled: BOOLByReference): Boolean
}

/**
 * Specific exception for MFPlayer
 */
class MFPlayerException(message: String) : Exception(message)

/**
 * Data class representing the player state
 */
data class PlayerState(
    var mediaPlayer: Pointer? = null,
    var mediaItem: Pointer? = null,
    var isPlaying: Boolean = false,
    var duration: Long = 0
)