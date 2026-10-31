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
    val hasRemoteParticipant: StateFlow<Boolean>
    val isMuted: StateFlow<Boolean>
    val isSpeakerphoneOn: StateFlow<Boolean>
    val selectedAudioDevice: StateFlow<AppAudioDevice>
    val availableAudioDevices: StateFlow<Set<AppAudioDevice>>
    val isMutedByAudioFocus: StateFlow<Boolean>
    val isInterruptedBySystemCall: StateFlow<Boolean>
    val isMicPublished: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>

    fun connect(url: String, roomName: String, token: String, isVideo: Boolean = false)
    fun disconnect()
    fun enableMicrophone()
    fun setMicrophoneMuted(muted: Boolean)
    fun setSpeakerphoneEnabled(enabled: Boolean)
    fun selectAudioDevice(device: AppAudioDevice): Boolean
    fun setBluetoothPermission(granted: Boolean)
}
