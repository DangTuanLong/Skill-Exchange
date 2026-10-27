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

    var lastConnectedUrl: String? = null
        private set
    var lastConnectedRoomName: String? = null
        private set
    var lastConnectedToken: String? = null
        private set

    override fun connect(url: String, roomName: String, token: String) {
        lastConnectedUrl = url
        lastConnectedRoomName = roomName
        lastConnectedToken = token
        _status.value = CallEngineStatus.CONNECTING
        if (autoConnect) {
            _status.value = CallEngineStatus.CONNECTED
        }
    }

    override fun disconnect() {
        _status.value = CallEngineStatus.DISCONNECTED
    }

    override fun setMicrophoneMuted(muted: Boolean) {
        _isMuted.value = muted
    }

    override fun setSpeakerphoneEnabled(enabled: Boolean) {
        _isSpeakerphoneOn.value = enabled
    }

    fun simulateStatus(newStatus: CallEngineStatus) {
        _status.value = newStatus
    }
}
