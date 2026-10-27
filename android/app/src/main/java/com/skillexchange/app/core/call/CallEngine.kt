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

    fun connect(url: String, roomName: String, token: String)
    fun disconnect()
    fun setMicrophoneMuted(muted: Boolean)
    fun setSpeakerphoneEnabled(enabled: Boolean)
}
