package com.skillexchange.app.core.call

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeCallEngine(
    private val autoConnect: Boolean = true
) : CallEngine {

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

    var lastConnectedUrl: String? = null
        private set
    var lastConnectedRoomName: String? = null
        private set
    var lastConnectedToken: String? = null
        private set
    var lastConnectedIsVideo: Boolean = false
        private set

    var hasBluetoothPermission: Boolean = false
        private set

    override fun connect(url: String, roomName: String, token: String, isVideo: Boolean) {
        lastConnectedUrl = url
        lastConnectedRoomName = roomName
        lastConnectedToken = token
        lastConnectedIsVideo = isVideo
        _isMuted.value = false
        _isSpeakerphoneOn.value = isVideo
        _selectedAudioDevice.value = if (isVideo) AppAudioDevice.SPEAKER else AppAudioDevice.EARPIECE
        _isMutedByAudioFocus.value = false
        _isInterruptedBySystemCall.value = false
        _isMicPublished.value = true
        _isCameraEnabled.value = isVideo
        _isFrontCamera.value = true
        _errorMessage.value = null
        audioRoutingManager = AudioRoutingManager(
            isVideo = isVideo,
            initialAvailableDevices = _availableAudioDevices.value,
            initialHasBluetoothPermission = hasBluetoothPermission
        )
        _selectedAudioDevice.value = audioRoutingManager.selectedDevice.value
        _isSpeakerphoneOn.value = (_selectedAudioDevice.value == AppAudioDevice.SPEAKER)
        _status.value = CallEngineStatus.CONNECTING
        if (autoConnect) {
            _status.value = CallEngineStatus.CONNECTED
            _hasRemoteParticipant.value = true
        }
    }

    override fun disconnect() {
        _status.value = CallEngineStatus.DISCONNECTED
        _isMuted.value = false
        _isSpeakerphoneOn.value = false
        _selectedAudioDevice.value = AppAudioDevice.EARPIECE
        _isMutedByAudioFocus.value = false
        _isInterruptedBySystemCall.value = false
        _isMicPublished.value = false
        _hasRemoteParticipant.value = false
        _isCameraEnabled.value = false
        _isFrontCamera.value = true
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _errorMessage.value = null
    }

    override fun enableMicrophone() {
        _isMuted.value = false
        _isMicPublished.value = true
        _errorMessage.value = null
    }

    override fun setMicrophoneMuted(muted: Boolean) {
        _isMuted.value = muted
        _isMicPublished.value = !muted
    }

    override fun setSpeakerphoneEnabled(enabled: Boolean) {
        val target = if (enabled) AppAudioDevice.SPEAKER else AppAudioDevice.EARPIECE
        selectAudioDevice(target)
    }

    override fun selectAudioDevice(device: AppAudioDevice): Boolean {
        val success = audioRoutingManager.selectDevice(device)
        if (success) {
            _selectedAudioDevice.value = device
            _isSpeakerphoneOn.value = (device == AppAudioDevice.SPEAKER)
        }
        return success
    }

    override fun setBluetoothPermission(granted: Boolean) {
        hasBluetoothPermission = granted
        audioRoutingManager.setBluetoothPermission(granted)
        _selectedAudioDevice.value = audioRoutingManager.selectedDevice.value
        _isSpeakerphoneOn.value = (_selectedAudioDevice.value == AppAudioDevice.SPEAKER)
    }

    override fun setCameraEnabled(enabled: Boolean) {
        _isCameraEnabled.value = enabled
    }

    override fun flipCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
    }

    override fun initVideoRenderer(view: io.livekit.android.renderer.SurfaceViewRenderer) {
        // No-op trong JVM test
    }

    fun simulateStatus(newStatus: CallEngineStatus) {
        _status.value = newStatus
    }

    fun simulateRemoteParticipant(hasRemote: Boolean) {
        _hasRemoteParticipant.value = hasRemote
    }

    fun simulateAvailableDevices(devices: Set<AppAudioDevice>) {
        _availableAudioDevices.value = devices
        audioRoutingManager.setAvailableDevices(devices)
        _selectedAudioDevice.value = audioRoutingManager.selectedDevice.value
        _isSpeakerphoneOn.value = (_selectedAudioDevice.value == AppAudioDevice.SPEAKER)
    }

    fun simulateAudioFocusChange(focusChange: Int) {
        audioRoutingManager.onAudioFocusChange(focusChange)
        _isMutedByAudioFocus.value = audioRoutingManager.isMutedByAudioFocus.value
        _isInterruptedBySystemCall.value = audioRoutingManager.isInterruptedBySystemCall.value
    }
}

