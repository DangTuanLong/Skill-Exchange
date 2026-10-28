package com.skillexchange.app.core.call

import kotlinx.coroutines.flow.StateFlow

enum class CallEngineStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

interface CallEngine {
    val status: StateFlow<CallEngineStatus>
    val isMuted: StateFlow<Boolean>
    val isSpeakerphoneOn: StateFlow<Boolean>
    val selectedAudioDevice: StateFlow<AppAudioDevice>
    val availableAudioDevices: StateFlow<Set<AppAudioDevice>>
    val isMutedByAudioFocus: StateFlow<Boolean>
    val isInterruptedBySystemCall: StateFlow<Boolean>

    fun connect(url: String, roomName: String, token: String, isVideo: Boolean = false)
    fun disconnect()
    fun setMicrophoneMuted(muted: Boolean)
    fun setSpeakerphoneEnabled(enabled: Boolean)
    fun selectAudioDevice(device: AppAudioDevice): Boolean
    fun setBluetoothPermission(granted: Boolean)
}
