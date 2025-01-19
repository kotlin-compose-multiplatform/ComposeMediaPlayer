package io.github.kdroidfilter.composemediaplayer.windows

import com.sun.jna.*
import com.sun.jna.platform.win32.WinDef.BOOLByReference
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference

interface MFPlayerLibrary : Library {

    companion object {
        val INSTANCE: MFPlayerLibrary = Native.load("MFPlayer", MFPlayerLibrary::class.java)
    }

    // MFPMediaPlayer Functions
    fun MFPMediaPlayer_Init(hWnd: Pointer, callback: Pointer, mediaPlayer: PointerByReference): Boolean
    fun MFPMediaPlayer_Free(mediaPlayer: PointerByReference): Boolean

    fun MFPMediaPlayer_Play(mediaPlayer: Pointer): Boolean
    fun MFPMediaPlayer_Pause(mediaPlayer: Pointer): Boolean
    fun MFPMediaPlayer_Stop(mediaPlayer: Pointer): Boolean
    fun MFPMediaPlayer_Step(mediaPlayer: Pointer): Boolean
    fun MFPMediaPlayer_Toggle(mediaPlayer: Pointer): Boolean

    fun MFPMediaPlayer_ClearMediaItem(mediaPlayer: Pointer): Boolean
    fun MFPMediaPlayer_SetMediaItem(mediaPlayer: Pointer, mediaItem: Pointer): Boolean
    fun MFPMediaPlayer_GetMediaItem(mediaPlayer: Pointer, mediaItem: PointerByReference): Boolean
    fun MFPMediaPlayer_CreateMediaItemA(mediaPlayer: Pointer, url: String, userData: Int, mediaItem: PointerByReference): Boolean
    fun MFPMediaPlayer_CreateMediaItemW(mediaPlayer: Pointer, url: WString, userData: Int, mediaItem: PointerByReference): Boolean
    fun MFPMediaPlayer_CreateMediaItemFromObject(mediaPlayer: Pointer, obj: Pointer, userData: Int, mediaItem: PointerByReference): Boolean

    fun MFPMediaPlayer_GetState(mediaPlayer: Pointer, state: IntByReference): Boolean
    fun MFPMediaPlayer_SetPosition(mediaPlayer: Pointer, position: Long): Boolean
    fun MFPMediaPlayer_GetPosition(mediaPlayer: Pointer, position: LongByReference): Boolean
    fun MFPMediaPlayer_GetDuration(mediaPlayer: Pointer, duration: LongByReference): Boolean

    fun MFPMediaPlayer_SetRate(mediaPlayer: Pointer, rate: Float): Boolean
    fun MFPMediaPlayer_GetRate(mediaPlayer: Pointer, rate: FloatByReference): Boolean
    fun MFPMediaPlayer_GetSupportedRates(mediaPlayer: Pointer, forward: Boolean, slowestRate: FloatByReference, fastestRate: FloatByReference): Boolean

    fun MFPMediaPlayer_GetVolume(mediaPlayer: Pointer, volume: FloatByReference): Boolean
    fun MFPMediaPlayer_SetVolume(mediaPlayer: Pointer, volume: Float): Boolean
    fun MFPMediaPlayer_GetBalance(mediaPlayer: Pointer, balance: FloatByReference): Boolean
    fun MFPMediaPlayer_SetBalance(mediaPlayer: Pointer, balance: Float): Boolean
    fun MFPMediaPlayer_GetMute(mediaPlayer: Pointer, mute: BOOLByReference): Boolean
    fun MFPMediaPlayer_SetMute(mediaPlayer: Pointer, mute: Boolean): Boolean

    fun MFPMediaPlayer_GetNativeVideoSize(mediaPlayer: Pointer, width: IntByReference, height: IntByReference): Boolean
    fun MFPMediaPlayer_GetIdealVideoSize(mediaPlayer: Pointer, width: IntByReference, height: IntByReference): Boolean
    fun MFPMediaPlayer_SetVideoSourceRect(mediaPlayer: Pointer, rect: Pointer): Boolean
    fun MFPMediaPlayer_GetVideoSourceRect(mediaPlayer: Pointer, rect: Pointer): Boolean

    fun MFPMediaPlayer_SetAspectRatioMode(mediaPlayer: Pointer, mode: Int): Boolean
    fun MFPMediaPlayer_GetAspectRatioMode(mediaPlayer: Pointer, mode: IntByReference): Boolean

    fun MFPMediaPlayer_GetVideoWindow(mediaPlayer: Pointer, hwnd: PointerByReference): Boolean
    fun MFPMediaPlayer_UpdateVideo(mediaPlayer: Pointer): Boolean
    fun MFPMediaPlayer_SetBorderColor(mediaPlayer: Pointer, color: Int): Boolean
    fun MFPMediaPlayer_GetBorderColor(mediaPlayer: Pointer, color: IntByReference): Boolean

    fun MFPMediaPlayer_InsertEffect(mediaPlayer: Pointer, effect: Pointer, optional: Boolean): Boolean
    fun MFPMediaPlayer_RemoveEffect(mediaPlayer: Pointer, effect: Pointer): Boolean
    fun MFPMediaPlayer_RemoveAllEffects(mediaPlayer: Pointer): Boolean
    fun MFPMediaPlayer_Shutdown(mediaPlayer: Pointer): Boolean

    // MFPMediaItem Functions
    fun MFPMediaItem_Release(mediaItem: Pointer): Boolean
    fun MFPMediaItem_GetMediaPlayer(mediaItem: Pointer, mediaPlayer: PointerByReference): Boolean
    fun MFPMediaItem_GetURLA(mediaItem: Pointer, url: PointerByReference): Boolean
    fun MFPMediaItem_GetURLW(mediaItem: Pointer, url: PointerByReference): Boolean

    fun MFPMediaItem_SetUserData(mediaItem: Pointer, userData: Int): Boolean
    fun MFPMediaItem_GetUserData(mediaItem: Pointer, userData: IntByReference): Boolean

    fun MFPMediaItem_SetStartStopPosition(mediaItem: Pointer, start: Long, stop: Long): Boolean
    fun MFPMediaItem_GetStartStopPosition(mediaItem: Pointer, start: LongByReference, stop: LongByReference): Boolean

    fun MFPMediaItem_HasVideo(mediaItem: Pointer, hasVideo: BOOLByReference, selected: BOOLByReference): Boolean
    fun MFPMediaItem_HasAudio(mediaItem: Pointer, hasAudio: BOOLByReference, selected: BOOLByReference): Boolean
    fun MFPMediaItem_IsProtected(mediaItem: Pointer, isProtected: BOOLByReference): Boolean

    fun MFPMediaItem_GetDuration(mediaItem: Pointer, duration: LongByReference): Boolean
    fun MFPMediaItem_GetNumberOfStreams(mediaItem: Pointer, streamCount: IntByReference): Boolean
    fun MFPMediaItem_SetStreamSelection(mediaItem: Pointer, streamIndex: Int, enabled: Boolean): Boolean
    fun MFPMediaItem_GetStreamSelection(mediaItem: Pointer, streamIndex: Int, enabled: BOOLByReference): Boolean

    fun MFPMediaItem_GetStreamAttribute(mediaItem: Pointer, streamIndex: Int, attribute: Pointer, value: Pointer): Boolean
    fun MFPMediaItem_GetPresentationAttribute(mediaItem: Pointer, attribute: Pointer, value: Pointer): Boolean
    fun MFPMediaItem_GetCharacteristics(mediaItem: Pointer, characteristics: IntByReference): Boolean
    fun MFPMediaItem_GetMetadata(mediaItem: Pointer, metadata: PointerByReference): Boolean

    fun MFPMediaItem_SetStreamSink(mediaItem: Pointer, streamIndex: Int, sink: Pointer): Boolean

    // Media Information
    fun MFPMediaItem_StreamTable(mediaItem: Pointer, streamCount: IntByReference, streamTable: PointerByReference): Boolean

    // Misc
    fun MFPConvertMSTimeToTimeStringA(milliseconds: Long, timeString: PointerByReference, timeFormat: Int): Boolean
    fun MFPConvertMSTimeToTimeStringW(milliseconds: Long, timeString: PointerByReference, timeFormat: Int): Boolean

    fun MFPConvertStringToAnsi(input: WString, output: PointerByReference): Boolean
    fun MFPConvertStringToWide(input: String, output: PointerByReference): Boolean
    fun MFPConvertStringFree(string: Pointer): Boolean
}

class MFVideoNormalizedRect : Structure() {
    @JvmField var left: Float = 0.0f
    @JvmField var top: Float = 0.0f
    @JvmField var right: Float = 1.0f
    @JvmField var bottom: Float = 1.0f

    override fun getFieldOrder(): List<String> {
        return listOf("left", "top", "right", "bottom")
    }

    companion object {
        fun create(): MFVideoNormalizedRect {
            val rect = MFVideoNormalizedRect()
            rect.write() // Ensure native memory is allocated and written
            return rect
        }
    }
}



// Constants
object MediaPlayerConstants {
    const val MFP_MEDIAPLAYER_STATE_EMPTY = 0
    const val MFP_MEDIAPLAYER_STATE_STOPPED = 1
    const val MFP_MEDIAPLAYER_STATE_PLAYING = 2
    const val MFP_MEDIAPLAYER_STATE_PAUSED = 3
    const val MFP_MEDIAPLAYER_STATE_SHUTDOWN = 4

    const val DEFAULT_WINDOW_WIDTH = 800
    const val DEFAULT_WINDOW_HEIGHT = 600
    const val MFP_ASPECT_RATIO_PRESERVE = 1
    const val MFP_VIDEO_BORDER_COLOR = 0x00000000 // Black
}

// Data class for player state
data class PlayerState(
    var mediaPlayer: Pointer? = null,
    var mediaItem: Pointer? = null,
    var isPlaying: Boolean = false,
    var duration: Long = 0
)

