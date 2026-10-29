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
        }
    }

    override fun disconnect() {
        _status.value = CallEngineStatus.DISCONNECTED
        _isMuted.value = false
        _isSpeakerphoneOn.value = false
        _selectedAudioDevice.value = AppAudioDevice.EARPIECE
        _isMutedByAudioFocus.value = false
        _isInterruptedBySystemCall.value = false
    }

    override fun setMicrophoneMuted(muted: Boolean) {
        _isMuted.value = muted
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

    fun simulateStatus(newStatus: CallEngineStatus) {
        _status.value = newStatus
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

