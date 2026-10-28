package com.skillexchange.app.core.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.twilio.audioswitch.AudioDevice
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.audio.AudioSwitchHandler
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

    private var audioRoutingManager = AudioRoutingManager(isVideo = false)
    private val _selectedAudioDevice = MutableStateFlow(AppAudioDevice.EARPIECE)
    override val selectedAudioDevice: StateFlow<AppAudioDevice> = _selectedAudioDevice.asStateFlow()

    private val _availableAudioDevices = MutableStateFlow<Set<AppAudioDevice>>(setOf(AppAudioDevice.EARPIECE, AppAudioDevice.SPEAKER))
    override val availableAudioDevices: StateFlow<Set<AppAudioDevice>> = _availableAudioDevices.asStateFlow()

    private val _isMutedByAudioFocus = MutableStateFlow(false)
    override val isMutedByAudioFocus: StateFlow<Boolean> = _isMutedByAudioFocus.asStateFlow()

    private val _isInterruptedBySystemCall = MutableStateFlow(false)
    override val isInterruptedBySystemCall: StateFlow<Boolean> = _isInterruptedBySystemCall.asStateFlow()

    private var room: Room? = null
    private var eventsJob: Job? = null
    private var audioSwitchHandler: AudioSwitchHandler? = null

    init {
        checkInitialBluetoothPermission()
    }

    private fun checkInitialBluetoothPermission() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        audioRoutingManager.setBluetoothPermission(hasPermission)
    }

    override fun connect(url: String, roomName: String, token: String, isVideo: Boolean) {
        if (_status.value == CallEngineStatus.CONNECTED || _status.value == CallEngineStatus.CONNECTING) {
            Log.w(tag, "CallEngine đang ở trạng thái ${_status.value}, bỏ qua yêu cầu kết nối lại.")
            return
        }

        _status.value = CallEngineStatus.CONNECTING

        coroutineScope.launch {
            try {
                // Khởi tạo AudioRoutingManager theo loại cuộc gọi (thoại = earpiece, video = speaker)
                checkInitialBluetoothPermission()
                audioRoutingManager = AudioRoutingManager(
                    isVideo = isVideo,
                    initialAvailableDevices = _availableAudioDevices.value,
                    initialHasBluetoothPermission = audioRoutingManager.hasBluetoothPermission.value
                )
                _selectedAudioDevice.value = audioRoutingManager.selectedDevice.value
                _isSpeakerphoneOn.value = (_selectedAudioDevice.value == AppAudioDevice.SPEAKER)

                // Khởi tạo AudioSwitchHandler của LiveKit SDK với cấu hình ưu tiên
                val handler = AudioSwitchHandler(context.applicationContext).apply {
                    loggingEnabled = true
                    preferredDeviceList = if (isVideo) {
                        listOf(
                            AudioDevice.BluetoothHeadset::class.java,
                            AudioDevice.WiredHeadset::class.java,
                            AudioDevice.Speakerphone::class.java,
                            AudioDevice.Earpiece::class.java
                        )
                    } else {
                        listOf(
                            AudioDevice.BluetoothHeadset::class.java,
                            AudioDevice.WiredHeadset::class.java,
                            AudioDevice.Earpiece::class.java,
                            AudioDevice.Speakerphone::class.java
                        )
                    }
                }
                audioSwitchHandler = handler

                // Lắng nghe thay đổi thiết bị từ AudioSwitchHandler
                handler.registerAudioDeviceChangeListener { devices, selected ->
                    val mappedDevices = devices.mapNotNull { it.toAppAudioDevice() }.toSet()
                    audioRoutingManager.setAvailableDevices(mappedDevices)
                    _availableAudioDevices.value = audioRoutingManager.availableDevices.value
                    _selectedAudioDevice.value = audioRoutingManager.selectedDevice.value
                    _isSpeakerphoneOn.value = (_selectedAudioDevice.value == AppAudioDevice.SPEAKER)
                }

                // Lắng nghe thay đổi Audio Focus (cuộc gọi SIM / app khác chen vào)
                handler.registerOnAudioFocusChangeListener { focusChange ->
                    audioRoutingManager.onAudioFocusChange(focusChange)
                    _isMutedByAudioFocus.value = audioRoutingManager.isMutedByAudioFocus.value
                    _isInterruptedBySystemCall.value = audioRoutingManager.isInterruptedBySystemCall.value

                    if (audioRoutingManager.isMutedByAudioFocus.value) {
                        // Tạm ngắt microphone khi cuộc gọi SIM chen ngang
                        launch {
                            runCatching { room?.localParticipant?.setMicrophoneEnabled(false) }
                        }
                    } else {
                        // Khôi phục lại trạng thái microphone ban đầu
                        if (!_isMuted.value) {
                            launch {
                                runCatching { room?.localParticipant?.setMicrophoneEnabled(true) }
                            }
                        }
                    }
                }

                // Khởi tạo LiveKit Room kèm AudioSwitchHandler
                val currentRoom = LiveKit.create(
                    appContext = context.applicationContext,
                    overrides = LiveKitOverrides(
                        audioOptions = AudioOptions(audioHandler = handler)
                    )
                ).also {
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
                            else -> {}
                        }
                    }
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
                audioSwitchHandler?.stop()
                audioSwitchHandler = null
                room?.disconnect()
                room?.release()
                room = null
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
        val targetDevice = if (enabled) AppAudioDevice.SPEAKER else AppAudioDevice.EARPIECE
        selectAudioDevice(targetDevice)
    }

    override fun selectAudioDevice(device: AppAudioDevice): Boolean {
        val success = audioRoutingManager.selectDevice(device)
        if (success) {
            _selectedAudioDevice.value = device
            _isSpeakerphoneOn.value = (device == AppAudioDevice.SPEAKER)

            audioSwitchHandler?.let { handler ->
                val targetAudioDevice = handler.availableAudioDevices.find { it.toAppAudioDevice() == device }
                if (targetAudioDevice != null) {
                    handler.selectDevice(targetAudioDevice)
                }
            }
        }
        return success
    }

    override fun setBluetoothPermission(granted: Boolean) {
        audioRoutingManager.setBluetoothPermission(granted)
    }

    private fun AudioDevice.toAppAudioDevice(): AppAudioDevice? {
        return when (this) {
            is AudioDevice.Speakerphone -> AppAudioDevice.SPEAKER
            is AudioDevice.Earpiece -> AppAudioDevice.EARPIECE
            is AudioDevice.WiredHeadset -> AppAudioDevice.WIRED
            is AudioDevice.BluetoothHeadset -> AppAudioDevice.BLUETOOTH
            else -> null
        }
    }
}
