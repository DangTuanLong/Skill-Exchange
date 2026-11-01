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

    private val _isMicPublished = MutableStateFlow(false)
    override val isMicPublished: StateFlow<Boolean> = _isMicPublished.asStateFlow()

    private val _hasRemoteParticipant = MutableStateFlow(false)
    override val hasRemoteParticipant: StateFlow<Boolean> = _hasRemoteParticipant.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(false)
    override val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    override val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    private val _localVideoTrack = MutableStateFlow<io.livekit.android.room.track.VideoTrack?>(null)
    override val localVideoTrack: StateFlow<io.livekit.android.room.track.VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<io.livekit.android.room.track.VideoTrack?>(null)
    override val remoteVideoTrack: StateFlow<io.livekit.android.room.track.VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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

        // Reset trạng thái ở đầu connect() (TASK-080 Mục 3)
        // Trên bản DEBUG: Mặc định bật loa ngoài (SPEAKER) để máy ảo/dev phát qua loa máy tính host
        val defaultSpeaker = isVideo || com.skillexchange.app.BuildConfig.DEBUG
        _isMuted.value = false
        _isMicPublished.value = false
        _hasRemoteParticipant.value = false
        _isCameraEnabled.value = isVideo
        _isFrontCamera.value = true
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _errorMessage.value = null
        _isSpeakerphoneOn.value = defaultSpeaker
        _selectedAudioDevice.value = if (defaultSpeaker) AppAudioDevice.SPEAKER else AppAudioDevice.EARPIECE
        _isMutedByAudioFocus.value = false
        _isInterruptedBySystemCall.value = false
        _status.value = CallEngineStatus.CONNECTING

        coroutineScope.launch {
            try {
                // Khởi tạo AudioRoutingManager theo loại cuộc gọi (DEBUG = speaker, Release thoại = earpiece)
                checkInitialBluetoothPermission()
                audioRoutingManager = AudioRoutingManager(
                    isVideo = isVideo,
                    initialAvailableDevices = _availableAudioDevices.value,
                    initialHasBluetoothPermission = audioRoutingManager.hasBluetoothPermission.value,
                    defaultToSpeaker = com.skillexchange.app.BuildConfig.DEBUG
                )
                _selectedAudioDevice.value = audioRoutingManager.selectedDevice.value
                _isSpeakerphoneOn.value = (_selectedAudioDevice.value == AppAudioDevice.SPEAKER)

                // Khởi tạo AudioSwitchHandler và nạp native WebRTC C++ (LiveKit.create) trên Dispatchers.IO (TASK-080 Mục 2)
                val (handler, currentRoom) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val h = AudioSwitchHandler(context.applicationContext).apply {
                        loggingEnabled = true
                        preferredDeviceList = if (isVideo || com.skillexchange.app.BuildConfig.DEBUG) {
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
                    val r = LiveKit.create(
                        appContext = context.applicationContext,
                        overrides = LiveKitOverrides(
                            audioOptions = AudioOptions(audioHandler = h)
                        )
                    )
                    Pair(h, r)
                }
                audioSwitchHandler = handler
                room = currentRoom

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
                            runCatching { currentRoom.localParticipant?.setMicrophoneEnabled(false) }
                        }
                    } else {
                        // Khôi phục lại trạng thái microphone ban đầu
                        if (!_isMuted.value) {
                            launch {
                                runCatching { currentRoom.localParticipant?.setMicrophoneEnabled(true) }
                            }
                        }
                    }
                }

                // Hủy lắng nghe cũ nếu có
                eventsJob?.cancel()
                eventsJob = launch {
                    currentRoom.events.events.collect { event ->
                        when (event) {
                            is RoomEvent.Connected -> {
                                val hasRemote = currentRoom.remoteParticipants.isNotEmpty()
                                _hasRemoteParticipant.value = hasRemote
                                Log.d(tag, "LiveKit Room đã kết nối thành công: ${event.room.name}. Remote participants: ${currentRoom.remoteParticipants.size}. Thiết bị đang chọn: ${_selectedAudioDevice.value.displayName}")
                                _status.value = CallEngineStatus.CONNECTED

                                // Mặc định bật mic theo trạng thái mute hiện tại
                                launch {
                                    try {
                                        currentRoom.localParticipant.setMicrophoneEnabled(!_isMuted.value)
                                        _isMicPublished.value = !_isMuted.value
                                        _errorMessage.value = null
                                        Log.d(tag, "Kích hoạt micro tại RoomEvent.Connected thành công: published=${!_isMuted.value}")
                                    } catch (e: Exception) {
                                        val errMsg = "Không thể kích hoạt micro khi vào phòng: ${e.localizedMessage ?: e.message}"
                                        Log.e(tag, errMsg, e)
                                        _errorMessage.value = errMsg
                                        _isMicPublished.value = false
                                    }
                                }

                                // Kích hoạt camera nếu là cuộc gọi video
                                if (isVideo) {
                                    launch {
                                        try {
                                            currentRoom.localParticipant.setCameraEnabled(true)
                                            _isCameraEnabled.value = true
                                            val pub = currentRoom.localParticipant.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)
                                            _localVideoTrack.value = pub?.track as? io.livekit.android.room.track.VideoTrack
                                            Log.d(tag, "Camera cục bộ đã kích hoạt thành công: track=${_localVideoTrack.value}")
                                        } catch (e: Exception) {
                                            val errMsg = "Không thể kích hoạt Camera: ${e.localizedMessage ?: e.message}"
                                            Log.e(tag, errMsg, e)
                                            _errorMessage.value = errMsg
                                            _isCameraEnabled.value = false
                                        }
                                    }
                                }

                                // Kiểm tra nếu đối tác đã có sẵn video track
                                currentRoom.remoteParticipants.values.firstOrNull()?.let { remote ->
                                    val videoPub = remote.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)
                                    (videoPub?.track as? io.livekit.android.room.track.VideoTrack)?.let { vt ->
                                        _remoteVideoTrack.value = vt
                                    }
                                }
                            }
                            is RoomEvent.ParticipantConnected -> {
                                Log.d(tag, "Đối tác đã tham gia phòng LiveKit: ${event.participant.identity}")
                                _hasRemoteParticipant.value = true
                                val videoPub = event.participant.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)
                                (videoPub?.track as? io.livekit.android.room.track.VideoTrack)?.let { vt ->
                                    _remoteVideoTrack.value = vt
                                }
                            }
                            is RoomEvent.ParticipantDisconnected -> {
                                val remaining = currentRoom.remoteParticipants.isNotEmpty()
                                Log.d(tag, "Đối tác đã rời phòng LiveKit: ${event.participant.identity}. Còn lại: ${currentRoom.remoteParticipants.size}")
                                _hasRemoteParticipant.value = remaining
                                if (!remaining) {
                                    _remoteVideoTrack.value = null
                                }
                            }
                            is RoomEvent.TrackSubscribed -> {
                                if (event.track is io.livekit.android.room.track.VideoTrack) {
                                    Log.d(tag, "Đã nhận video track từ đối tác: ${event.participant.identity}")
                                    _remoteVideoTrack.value = event.track as io.livekit.android.room.track.VideoTrack
                                }
                            }
                            is RoomEvent.TrackUnsubscribed -> {
                                if (event.track == _remoteVideoTrack.value) {
                                    Log.d(tag, "Đối tác đã tắt/hủy video track")
                                    _remoteVideoTrack.value = null
                                }
                            }
                            is RoomEvent.TrackPublished -> {
                                if (event.publication.source == io.livekit.android.room.track.Track.Source.CAMERA) {
                                    _localVideoTrack.value = event.publication.track as? io.livekit.android.room.track.VideoTrack
                                }
                            }
                            is RoomEvent.TrackUnpublished -> {
                                if (event.publication.source == io.livekit.android.room.track.Track.Source.CAMERA) {
                                    _localVideoTrack.value = null
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

        // Reset trạng thái trong disconnect() (TASK-080 Mục 3)
        _isMuted.value = false
        _isMicPublished.value = false
        _hasRemoteParticipant.value = false
        _isCameraEnabled.value = false
        _isFrontCamera.value = true
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _errorMessage.value = null
        _isSpeakerphoneOn.value = false
        _selectedAudioDevice.value = if (com.skillexchange.app.BuildConfig.DEBUG) AppAudioDevice.SPEAKER else AppAudioDevice.EARPIECE
        _isMutedByAudioFocus.value = false
        _isInterruptedBySystemCall.value = false

        coroutineScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    audioSwitchHandler?.stop()
                    audioSwitchHandler = null
                    room?.disconnect()
                    room?.release()
                    room = null
                }
            }
        }
    }

    override fun enableMicrophone() {
        _isMuted.value = false
        coroutineScope.launch {
            try {
                val current = room?.localParticipant
                if (current != null) {
                    current.setMicrophoneEnabled(true)
                    _isMicPublished.value = true
                    _errorMessage.value = null
                    Log.d(tag, "Kích hoạt micro thành công sau khi cấp quyền. Mic published: true, Thiết bị: ${_selectedAudioDevice.value.displayName}")
                } else {
                    Log.w(tag, "enableMicrophone gọi khi localParticipant chưa sẵn sàng (đang kết nối). Sẽ tự kích hoạt ở RoomEvent.Connected.")
                }
            } catch (e: Exception) {
                val errMsg = "Không thể kích hoạt micro: ${e.localizedMessage ?: e.message}"
                Log.e(tag, errMsg, e)
                _errorMessage.value = errMsg
                _isMicPublished.value = false
            }
        }
    }

    override fun setMicrophoneMuted(muted: Boolean) {
        _isMuted.value = muted
        coroutineScope.launch {
            try {
                val current = room?.localParticipant
                if (current != null) {
                    current.setMicrophoneEnabled(!muted)
                    _isMicPublished.value = !muted
                    _errorMessage.value = null
                    Log.d(tag, "setMicrophoneMuted($muted) thành công. Published: ${!muted}, Thiết bị: ${_selectedAudioDevice.value.displayName}")
                }
            } catch (e: Exception) {
                val errMsg = "Lỗi khi thay đổi trạng thái micro: ${e.localizedMessage ?: e.message}"
                Log.e(tag, errMsg, e)
                _errorMessage.value = errMsg
                if (!muted) {
                    _isMicPublished.value = false
                }
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
            Log.d(tag, "Đã chuyển thiết bị âm thanh sang: ${device.displayName}. Mic published: ${_isMicPublished.value}")
        }
        return success
    }

    override fun setBluetoothPermission(granted: Boolean) {
        audioRoutingManager.setBluetoothPermission(granted)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        _isCameraEnabled.value = enabled
        coroutineScope.launch {
            try {
                val lp = room?.localParticipant
                if (lp != null) {
                    lp.setCameraEnabled(enabled)
                    if (enabled) {
                        val pub = lp.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)
                        _localVideoTrack.value = pub?.track as? io.livekit.android.room.track.VideoTrack
                    } else {
                        _localVideoTrack.value = null
                    }
                    Log.d(tag, "setCameraEnabled($enabled) thành công. Track=${_localVideoTrack.value}")
                }
            } catch (e: Exception) {
                val errMsg = "Lỗi khi bật/tắt camera: ${e.localizedMessage ?: e.message}"
                Log.e(tag, errMsg, e)
                _errorMessage.value = errMsg
                _isCameraEnabled.value = !enabled
            }
        }
    }

    override fun flipCamera() {
        coroutineScope.launch {
            try {
                val lp = room?.localParticipant
                val localTrack = lp?.getTrackPublication(io.livekit.android.room.track.Track.Source.CAMERA)?.track as? io.livekit.android.room.track.LocalVideoTrack
                if (localTrack != null) {
                    localTrack.switchCamera()
                    _isFrontCamera.value = !_isFrontCamera.value
                    Log.d(tag, "Đã lật camera. Front camera: ${_isFrontCamera.value}")
                } else {
                    Log.w(tag, "flipCamera gọi khi localVideoTrack chưa khả dụng")
                }
            } catch (e: Exception) {
                Log.e(tag, "Lỗi khi lật camera: ${e.message}", e)
                _errorMessage.value = "Không thể chuyển camera: ${e.message}"
            }
        }
    }

    override fun initVideoRenderer(view: io.livekit.android.renderer.SurfaceViewRenderer) {
        try {
            room?.initVideoRenderer(view)
        } catch (e: Exception) {
            Log.e(tag, "Lỗi khi khởi tạo VideoRenderer: ${e.message}", e)
        }
    }

    private fun AudioDevice.toAppAudioDevice(): AppAudioDevice? {
        return when (this) {
            is AudioDevice.Speakerphone -> AppAudioDevice.SPEAKER
            is AudioDevice.Earpiece -> AppAudioDevice.EARPIECE
            is AudioDevice.WiredHeadset -> AppAudioDevice.WIRED
            is AudioDevice.BluetoothHeadset -> AppAudioDevice.BLUETOOTH
        }
    }
}
