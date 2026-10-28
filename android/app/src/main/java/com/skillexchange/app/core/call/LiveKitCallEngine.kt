package com.skillexchange.app.core.call

import android.content.Context
import android.media.AudioManager
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LiveKitCallEngine(
    private val context: Context,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
) : CallEngine {

    private val tag = "LiveKitCallEngine"

    private val _status = MutableStateFlow(CallEngineStatus.IDLE)
    override val status: StateFlow<CallEngineStatus> = _status.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerphoneOn = MutableStateFlow(false)
    override val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    private var room: Room? = null
    private var eventsJob: Job? = null
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    override fun connect(url: String, roomName: String, token: String) {
        if (_status.value == CallEngineStatus.CONNECTED || _status.value == CallEngineStatus.CONNECTING) {
            Log.w(tag, "CallEngine đang ở trạng thái ${_status.value}, bỏ qua yêu cầu kết nối lại.")
            return
        }

        _status.value = CallEngineStatus.CONNECTING

        coroutineScope.launch {
            try {
                // Khởi tạo LiveKit Room
                val currentRoom = room ?: LiveKit.create(appContext = context.applicationContext).also {
                    room = it
                }

                // Hủy lắng nghe cũ nếu có
                eventsJob?.cancel()
                eventsJob = launch {
                    currentRoom.events.events.collect { event ->
                        when (event) {
                            is RoomEvent.Connected -> {
                                Log.d(tag, "LiveKit Room đã kết nối thành công: ${event.room.name}")
                                _status.value = CallEngineStatus.CONNECTED
                                // Mặc định bật mic theo trạng thái mute hiện tại
                                launch {
                                    runCatching {
                                        currentRoom.localParticipant.setMicrophoneEnabled(!_isMuted.value)
                                    }
                                }
                            }
                            is RoomEvent.Disconnected -> {
                                Log.d(tag, "LiveKit Room đã ngắt kết nối. Error: ${event.error?.message}")
                                _status.value = CallEngineStatus.DISCONNECTED
                            }
                            is RoomEvent.FailedToConnect -> {
                                Log.e(tag, "LiveKit kết nối thất bại: ${event.error?.message}")
                                _status.value = CallEngineStatus.ERROR
                            }
                            is RoomEvent.Reconnecting -> {
                                Log.d(tag, "LiveKit đang kết nối lại do gián đoạn mạng...")
                            }
                            is RoomEvent.Reconnected -> {
                                Log.d(tag, "LiveKit đã kết nối lại thành công.")
                                _status.value = CallEngineStatus.CONNECTED
                            }
                            else -> {
                                // Các sự kiện khác (ParticipantConnected, etc.)
                            }
                        }
                    }
                }

                // Cấu hình âm thanh thoại
                audioManager?.let { am ->
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    am.isSpeakerphoneOn = _isSpeakerphoneOn.value
                }

                // Kết nối tới LiveKit Cloud SFU
                currentRoom.connect(url = url, token = token)

            } catch (e: Exception) {
                Log.e(tag, "Lỗi khi kết nối LiveKit Room: ${e.message}", e)
                _status.value = CallEngineStatus.ERROR
            }
        }
    }

    override fun disconnect() {
        Log.d(tag, "Đang ngắt kết nối LiveKit...")
        _status.value = CallEngineStatus.DISCONNECTED
        eventsJob?.cancel()
        eventsJob = null

        coroutineScope.launch {
            runCatching {
                room?.disconnect()
                room?.release()
                room = null
            }

            // Phục hồi cấu hình âm thanh
            audioManager?.let { am ->
                am.mode = AudioManager.MODE_NORMAL
                am.isSpeakerphoneOn = false
            }
        }
    }

    override fun setMicrophoneMuted(muted: Boolean) {
        _isMuted.value = muted
        coroutineScope.launch {
            runCatching {
                room?.localParticipant?.setMicrophoneEnabled(!muted)
            }
        }
    }

    override fun setSpeakerphoneEnabled(enabled: Boolean) {
        _isSpeakerphoneOn.value = enabled
        audioManager?.let { am ->
            am.isSpeakerphoneOn = enabled
        }
    }
}
